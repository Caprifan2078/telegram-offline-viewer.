package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Tiny synthetic Telegram export exercises the real parser, SQLite and portable media ZIP.
 * Generated data only: no personal chat content is used in CI. */
@RunWith(AndroidJUnit4.class)
public class ArchiveIntegrationTest {
    @Test public void parseGifVoiceRoundAndMeasureSearch() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=UUID.randomUUID().toString().replace("-","");
        File folder=new File(ctx.getCacheDir(),"synthetic-"+id);
        assertTrue(folder.mkdirs());
        File html=new File(folder,"messages.html");
        StringBuilder source=new StringBuilder("<html><body><div class='history'>");
        final int MESSAGES=480;
        for(int i=0;i<MESSAGES;i++){
            String media=(i%40==0)?"<a href='animations/animation"+i+".mp4'>GIF</a>":
                (i%40==1)?"<a href='gifs/sticker"+i+".gif'>GIF</a>":
                (i%40==2)?"<a href='round_video_messages/video"+i+".mp4'>Round</a>":
                (i%40==3)?"<a href='voice_messages/voice"+i+".ogg'>Voice</a>":
                (i%40==4)?"<a href='photos/photo"+i+".jpg'>Photo</a>":"";
            source.append("<div class='message default'><div class='body'><div class='from_name'>Тест</div>")
              .append("<div class='date details' title='2026-10-23 12:00'>12:00</div>")
              .append("<div class='text'>слово проверка номер ").append(i).append("</div>")
              .append(media).append("</div></div>");
        }
        source.append("</div></body></html>");
        try(Writer w=new OutputStreamWriter(new FileOutputStream(html),"UTF-8")){w.write(source.toString());}
        MessageDb db=new MessageDb(ctx,id);
        try{
            long begin=SystemClock.elapsedRealtimeNanos();
            int imported=TelegramImporter.importFiles(ctx.getContentResolver(),new DocumentFolder(ctx.getContentResolver(),folder),db,msg->{});
            long importMs=(SystemClock.elapsedRealtimeNanos()-begin)/1000000L;
            assertEquals(MESSAGES,imported);
            assertEquals(MESSAGES,db.totalMessages());
            assertEquals(24,db.mediaCount("gif"));
            assertEquals(12,db.mediaCount("round"));
            assertEquals(12,db.mediaCount("voice"));
            long start=SystemClock.elapsedRealtimeNanos();
            for(int i=0;i<40;i++)assertEquals(40,db.page(i*10,40,"",false,"",false,false).size());
            long pageMs=(SystemClock.elapsedRealtimeNanos()-start)/1000000L;
            start=SystemClock.elapsedRealtimeNanos();
            for(int i=0;i<15;i++)assertEquals(MESSAGES,db.count("проверк",false,"",false,false));
            long searchMs=(SystemClock.elapsedRealtimeNanos()-start)/1000000L;
            Log.i("TG_ARCHIVE_BENCH","synthetic_messages="+MESSAGES+" import_ms="+importMs+
                " page40_avg_ms="+(pageMs/40.0)+" fts_search_avg_ms="+(searchMs/15.0));
            assertEquals("gif",TelegramImporter.classify("animations/a.mp4",Jsoup.parse("<div class='message'><a href='animations/a.mp4'>gif</a></div>").selectFirst("a")));
            assertEquals("video",TelegramImporter.classify("video_files/a.mp4",Jsoup.parse("<a></a>").selectFirst("a")));
        }finally{
            db.close();ctx.deleteDatabase("telegram_"+id+".db");ArchiveFileImport.deleteTree(folder);
        }
    }

    @Test public void fullBackupRoundtripPreservesMediaPinsFavorites() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");
        String prefsName="tg-integration-"+key;
        SharedPreferences prefs=ctx.getSharedPreferences(prefsName,Context.MODE_PRIVATE);
        File folder=new File(ctx.getCacheDir(),"full-src-"+key);assertTrue(folder.mkdirs());
        File mediaDir=new File(folder,"photos");assertTrue(mediaDir.mkdirs());
        File photo=new File(mediaDir,"sample.jpg");
        byte[] sample="synthetic-photo-content".getBytes("UTF-8");
        try(FileOutputStream out=new FileOutputStream(photo)){out.write(sample);}
        File html=new File(folder,"messages.html");
        try(Writer out=new OutputStreamWriter(new FileOutputStream(html),"UTF-8")){
            out.write("<div class='history'><div class='message default'><div class='body'>"+
                "<div class='from_name'>Тест</div><div class='text'>Сохранить</div>"+
                "<a href='photos/sample.jpg'>Photo</a></div></div></div>");
        }
        MessageDb old=new MessageDb(ctx,key);
        File backup=new File(ctx.getCacheDir(),"integration-"+key+".zip");
        String restoredKey=null;
        try{
            assertEquals(1,TelegramImporter.importFiles(ctx.getContentResolver(),new DocumentFolder(ctx.getContentResolver(),folder),old,msg->{}));
            long msgId=old.page(0,1,"",false,"",false,false).get(0).id;
            old.togglePinned(msgId);
            old.toggleFavorite(msgId);
            JSONArray registry=new JSONArray();
            JSONObject chat=new JSONObject();chat.put("key",key);chat.put("name","Тестовый чат");chat.put("tree","local:"+folder.getAbsolutePath());
            registry.put(chat);
            assertTrue(prefs.edit().putString("archive_registry",registry.toString()).commit());
            long started=SystemClock.elapsedRealtimeNanos();
            try(FileOutputStream out=new FileOutputStream(backup)){FullBackup.export(ctx,prefs,out,msg->{});}
            long exportMs=(SystemClock.elapsedRealtimeNanos()-started)/1000000L;
            try(FileInputStream input=new FileInputStream(backup)){
                BackupVerifier.Result result=BackupVerifier.verifyStream(input,msg->{});
                assertTrue(result.full);assertTrue(result.files>=4);
            }
            int n=FullBackup.restore(ctx,prefs,android.net.Uri.fromFile(backup),msg->{});
            assertEquals(1,n);
            JSONArray restored=new JSONArray(prefs.getString("archive_registry","[]"));
            assertEquals(2,restored.length());
            JSONObject entry=restored.getJSONObject(1);
            restoredKey=entry.getString("key");
            MessageDb fresh=new MessageDb(ctx,restoredKey);
            try{
                assertEquals(1,fresh.totalMessages());
                assertEquals(1,fresh.pinned().size());
                assertEquals(1,fresh.count("",false,"",false,true));
                assertEquals(1,fresh.mediaCount("image"));
            }finally{fresh.close();}
            File restoredFolder=new File(entry.getString("tree").substring(6));
            File restoredPhoto=new File(restoredFolder,"photos/sample.jpg");
            assertTrue(restoredPhoto.isFile());
            byte[] found=new byte[sample.length];
            try(FileInputStream in=new FileInputStream(restoredPhoto)){assertEquals(found.length,in.read(found));}
            assertArrayEquals(sample,found);
            Log.i("TG_ARCHIVE_BENCH","synthetic_full_backup_export_ms="+exportMs+
                " archive_bytes="+backup.length()+" media_file_bytes="+sample.length);
            ArchiveFileImport.deleteTree(restoredFolder.getParentFile().getParentFile().getName().startsWith("restore-")?restoredFolder.getParentFile().getParentFile():restoredFolder);
        }finally{
            old.close();ctx.deleteDatabase("telegram_"+key+".db");
            if(restoredKey!=null)ctx.deleteDatabase("telegram_"+restoredKey+".db");
            prefs.edit().clear().commit();
            ArchiveFileImport.deleteTree(folder);backup.delete();
        }
    }
}
