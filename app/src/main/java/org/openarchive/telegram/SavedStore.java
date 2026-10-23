package org.openarchive.telegram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Personal notes and attachments independent of any imported conversation. */
final class SavedStore extends SQLiteOpenHelper {
    static final class Note {
        long id,created;
        String body,fileName,fileId,mime;
    }
    SavedStore(Context ctx){super(ctx,"global_saved_notes.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE saved_notes (id INTEGER PRIMARY KEY AUTOINCREMENT,created INTEGER NOT NULL,body TEXT NOT NULL,file_name TEXT,file_id TEXT,mime TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){}
    long add(String body,String fileName,String fileId,String mime){
        ContentValues v=new ContentValues();
        v.put("created",System.currentTimeMillis());v.put("body",body==null?"":body);
        v.put("file_name",fileName);v.put("file_id",fileId);v.put("mime",mime);
        return getWritableDatabase().insertOrThrow("saved_notes",null,v);
    }
    List<Note> recent(int limit){
        ArrayList<Note> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT id,created,body,file_name,file_id,mime FROM saved_notes ORDER BY created DESC,id DESC LIMIT "+Math.max(1,Math.min(200,limit)),null)){
            while(c.moveToNext()){
                Note n=new Note();n.id=c.getLong(0);n.created=c.getLong(1);
                n.body=c.getString(2);n.fileName=c.getString(3);n.fileId=c.getString(4);n.mime=c.getString(5);out.add(n);
            }
        }return out;
    }
    void delete(long id){getWritableDatabase().delete("saved_notes","id=?",new String[]{String.valueOf(id)});}
}
