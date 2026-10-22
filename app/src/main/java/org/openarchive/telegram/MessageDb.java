package org.openarchive.telegram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Private offline index: messages + media references; never stores copies of media bytes. */
class MessageDb extends SQLiteOpenHelper {
    static final class Item {
        long id; String author,date,body,media,mediaType; boolean favorite;
    }
    static final class Attachment {
        long id,messageId; String path,kind,author,date,body;
    }
    MessageDb(Context ctx,String key){super(ctx,"default".equals(key)?"telegram_archive.db":"telegram_"+key+".db",null,6);}
    @Override public void onConfigure(SQLiteDatabase d){super.onConfigure(d);d.enableWriteAheadLogging();}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT,media TEXT,media_type TEXT,body_fold TEXT,author_fold TEXT)");
        d.execSQL("CREATE TABLE staged_messages (id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT,media TEXT,media_type TEXT,body_fold TEXT,author_fold TEXT)");
        d.execSQL("CREATE VIRTUAL TABLE messages_fts USING fts4(author,body,date_text,content='messages',tokenize=unicode61)");
        mediaTables(d);
        d.execSQL("CREATE TABLE favorites (message_id INTEGER PRIMARY KEY)");
        d.execSQL("CREATE TABLE pins (message_id INTEGER PRIMARY KEY)");
    }
    private void mediaTables(SQLiteDatabase d){
        d.execSQL("CREATE TABLE IF NOT EXISTS attachments (id INTEGER PRIMARY KEY,message_id INTEGER NOT NULL,path TEXT NOT NULL,kind TEXT NOT NULL)");
        d.execSQL("CREATE TABLE IF NOT EXISTS staged_attachments (id INTEGER PRIMARY KEY,message_id INTEGER NOT NULL,path TEXT NOT NULL,kind TEXT NOT NULL)");
        d.execSQL("CREATE INDEX IF NOT EXISTS ix_attachment_kind ON attachments(kind,message_id)");
        d.execSQL("CREATE INDEX IF NOT EXISTS ix_attachment_message ON attachments(message_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int old,int now){
        if(old<2){
            d.execSQL("CREATE TABLE IF NOT EXISTS staged_messages (id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT,media TEXT,media_type TEXT)");
            d.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts4(author,body,date_text,content='messages',tokenize=unicode61)");
            d.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')");
        }
        if(old<3){
            mediaTables(d);
            d.execSQL("INSERT INTO attachments(message_id,path,kind) SELECT id,media,CASE WHEN lower(media) LIKE '%round_video%' OR lower(media) LIKE '%video_message%' THEN 'round' WHEN media_type='video' THEN 'video' WHEN media_type='audio' THEN 'audio' ELSE 'image' END FROM messages WHERE media IS NOT NULL AND media<>''");
        }
        if(old<4){d.execSQL("CREATE TABLE IF NOT EXISTS favorites (message_id INTEGER PRIMARY KEY)");}
        if(old<6)d.execSQL("CREATE TABLE IF NOT EXISTS pins (message_id INTEGER PRIMARY KEY)");
        if(old<5){
            d.execSQL("ALTER TABLE messages ADD COLUMN body_fold TEXT");
            d.execSQL("ALTER TABLE messages ADD COLUMN author_fold TEXT");
            d.execSQL("ALTER TABLE staged_messages ADD COLUMN body_fold TEXT");
            d.execSQL("ALTER TABLE staged_messages ADD COLUMN author_fold TEXT");
            // Existing data stays readable; a successful re-import populates Unicode casefold columns.

        }
    }
    boolean isPinned(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM pins WHERE message_id=?",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
    boolean togglePinned(long id){SQLiteDatabase d=getWritableDatabase();if(isPinned(id)){d.delete("pins","message_id=?",new String[]{String.valueOf(id)});return false;}ContentValues v=new ContentValues();v.put("message_id",id);d.insertWithOnConflict("pins",null,v,SQLiteDatabase.CONFLICT_IGNORE);return true;}
    List<Item> pinned(){ArrayList<Item> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT m.id,m.author,m.date_text,m.body FROM messages m JOIN pins p ON p.message_id=m.id ORDER BY m.id DESC LIMIT 100",null)){while(c.moveToNext()){Item i=new Item();i.id=c.getLong(0);i.author=c.getString(1);i.date=c.getString(2);i.body=c.getString(3);result.add(i);}}return result;}
    int totalMessages(){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM messages",null)){return c.moveToFirst()?c.getInt(0):0;}}
    private static String fold(String s){return (s==null?"":s).toLowerCase(Locale.ROOT);}
    void clearStage(SQLiteDatabase d){d.delete("staged_attachments",null,null);d.delete("staged_messages",null,null);}
    long stage(SQLiteDatabase d,String author,String date,String body,String media,String type){
        ContentValues v=new ContentValues();v.put("author",author);v.put("date_text",date);v.put("body",body);
        v.put("media",media);v.put("media_type",type);v.put("body_fold",fold(body));v.put("author_fold",fold(author));
        return d.insertOrThrow("staged_messages",null,v);
    }
    void stageAttachment(SQLiteDatabase d,long messageId,String path,String kind){
        ContentValues v=new ContentValues();v.put("message_id",messageId);v.put("path",path);v.put("kind",kind);
        d.insertOrThrow("staged_attachments",null,v);
    }
    void publishStaged(SQLiteDatabase d){
        d.beginTransaction();try{
            // Favorites are stored by stable chronological message ID across re-imports.
            d.delete("attachments",null,null);d.delete("messages",null,null);
            d.execSQL("INSERT INTO messages(id,author,date_text,body,media,media_type,body_fold,author_fold) SELECT id,author,date_text,body,media,media_type,body_fold,author_fold FROM staged_messages ORDER BY id");
            d.execSQL("INSERT INTO attachments(message_id,path,kind) SELECT message_id,path,kind FROM staged_attachments ORDER BY id");
            d.execSQL("DELETE FROM favorites WHERE message_id NOT IN (SELECT id FROM messages)");
            d.execSQL("DELETE FROM pins WHERE message_id NOT IN (SELECT id FROM messages)");
            d.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')");
            clearStage(d);d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    private static String fts(String raw){
        if(raw==null)return null;StringBuilder b=new StringBuilder();
        for(String t:fold(raw).split("[^\\p{L}\\p{N}]+"))if(!t.isEmpty()){
            if(b.length()>0)b.append(" AND ");b.append(t).append('*');
        }
        return b.length()==0?null:b.toString();
    }
    private static final class Filter{String clause;String[] args;Filter(String c,String[] a){clause=c;args=a;}}
    private Filter condition(String q,boolean deep,String author,boolean onlyMedia,boolean favorites){
        ArrayList<String> cond=new ArrayList<>();ArrayList<String> args=new ArrayList<>();
        if(q!=null&&!q.trim().isEmpty()){
            if(deep||fts(q)==null){
                cond.add("(COALESCE(body_fold,body) LIKE ? ESCAPE '\\' OR COALESCE(author_fold,author) LIKE ? ESCAPE '\\')");
                String w="%"+escapeLike(fold(q.trim()))+"%";args.add(w);args.add(w);
            }else{cond.add("messages.id IN (SELECT docid FROM messages_fts WHERE messages_fts MATCH ?)");args.add(fts(q));}
        }
        if(author!=null&&!author.trim().isEmpty()){cond.add("COALESCE(author_fold,author) LIKE ? ESCAPE '\\'");args.add("%"+escapeLike(fold(author.trim()))+"%");}
        if(onlyMedia)cond.add("EXISTS(SELECT 1 FROM attachments a WHERE a.message_id=messages.id)");
        if(favorites)cond.add("EXISTS(SELECT 1 FROM favorites f WHERE f.message_id=messages.id)");
        return new Filter(cond.isEmpty()?"":" WHERE "+android.text.TextUtils.join(" AND ",cond),args.toArray(new String[0]));
    }
    private static String escapeLike(String s){return s.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    int count(String q,boolean deep,String author,boolean onlyMedia,boolean favorites){
        Filter f=condition(q,deep,author,onlyMedia,favorites);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM messages"+f.clause,f.args)){return c.moveToFirst()?c.getInt(0):0;}
    }
    List<Item> page(int offset,int limit,String q,boolean deep,String author,boolean onlyMedia,boolean favorites){
        Filter f=condition(q,deep,author,onlyMedia,favorites);ArrayList<Item> list=new ArrayList<>();
        String sql="SELECT id,author,date_text,body,media,media_type,"+
                "EXISTS(SELECT 1 FROM favorites WHERE message_id=messages.id) FROM messages"+f.clause+
                " ORDER BY id LIMIT "+limit+" OFFSET "+Math.max(offset,0);
        try(Cursor c=getReadableDatabase().rawQuery(sql,f.args)){while(c.moveToNext()){
            Item m=new Item();m.id=c.getLong(0);m.author=c.getString(1);m.date=c.getString(2);m.body=c.getString(3);
            m.media=c.getString(4);m.mediaType=c.getString(5);m.favorite=c.getInt(6)!=0;list.add(m);
        }}
        return list;
    }
    boolean toggleFavorite(long id){
        SQLiteDatabase d=getWritableDatabase();
        if(isFavorite(id)){d.delete("favorites","message_id=?",new String[]{String.valueOf(id)});return false;}
        ContentValues v=new ContentValues();v.put("message_id",id);d.insertWithOnConflict("favorites",null,v,SQLiteDatabase.CONFLICT_IGNORE);return true;
    }
    boolean isFavorite(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM favorites WHERE message_id=?",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
    int positionFor(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM messages WHERE id < ?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getInt(0):-1;}}
    boolean hasMessage(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM messages WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
    int positionForDate(int y,int m,int d){
        String ru=String.format(Locale.ROOT,"%02d.%02d.%04d",d,m,y),iso=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m,d);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM messages WHERE date_text LIKE ? OR date_text LIKE ? ORDER BY id LIMIT 1",new String[]{"%"+ru+"%","%"+iso+"%"})){
            return c.moveToFirst()?positionFor(c.getLong(0)):-1;
        }
    }
    private String mediaWhere(String kind){
        switch(kind){
            case "all":return "";
            case "photos":return " WHERE a.kind IN ('image','sticker','gif')";
            case "videos":return " WHERE a.kind IN ('video','round')";
            case "audio":return " WHERE a.kind IN ('voice','audio')";
            default:return " WHERE a.kind='"+kind.replace("'","")+"'";
        }
    }
    int mediaCount(String kind){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM attachments a"+mediaWhere(kind),null)){return c.moveToFirst()?c.getInt(0):0;}}
    List<Attachment> mediaPage(String kind,int offset,int limit){
        List<Attachment> list=new ArrayList<>();
        String sql="SELECT a.id,a.message_id,a.path,a.kind,m.author,m.date_text,m.body FROM attachments a JOIN messages m ON a.message_id=m.id"+
                mediaWhere(kind)+" ORDER BY a.message_id,a.id LIMIT "+limit+" OFFSET "+Math.max(0,offset);
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext()){
            Attachment a=new Attachment();a.id=c.getLong(0);a.messageId=c.getLong(1);a.path=c.getString(2);a.kind=c.getString(3);
            a.author=c.getString(4);a.date=c.getString(5);a.body=c.getString(6);list.add(a);
        }}
        return list;
    }
    List<Attachment> attachmentsForMessage(long id){
        List<Attachment> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,message_id,path,kind FROM attachments WHERE message_id=? ORDER BY id",new String[]{String.valueOf(id)})){while(c.moveToNext()){
            Attachment a=new Attachment();a.id=c.getLong(0);a.messageId=c.getLong(1);a.path=c.getString(2);a.kind=c.getString(3);list.add(a);
        }}
        return list;
    }
}
