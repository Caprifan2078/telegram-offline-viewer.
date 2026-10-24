#!/usr/bin/env python3
from pathlib import Path
root=Path("app/src/main/java/org/openarchive/telegram")
main=root/"MainActivity.java"
s=main.read_text(encoding="utf-8")
start=s.index("    private void showHelp(){")
end=s.index("    private void animateEntrance(){", start)
s=s[:start]+'''    private void showHelp(){
        // In-app, searchable offline help screen. No links, permissions or servers.
        startActivity(new Intent(this,HelpActivity.class));
    }
'''+s[end:]
old='sideMenuItem(items,dialog,pane,"ⓘ  Справка","Объяснение всех функций",this::showHelp);'
assert old in s
s=s.replace(old,'sideMenuItem(items,dialog,pane,"ⓘ  Помощь и инструкция","Понятное руководство с поиском",this::showHelp);',1)
anchor='        TextView section=text("ПЕРЕПИСКИ",11,SUB);'
assert anchor in s
s=s.replace(anchor,'''        TextView guide=text("ⓘ   Как пользоваться приложением — помощь",14,ACCENT);
        guide.setTypeface(null,Typeface.BOLD);
        guide.setPadding(px(15),px(14),px(12),px(14));
        guide.setBackground(glass(PANEL,15));
        LinearLayout.LayoutParams gp=p(-1,-2);gp.setMargins(px(2),0,px(2),px(10));
        list.addView(guide,gp);guide.setOnClickListener(v->showHelp());
'''+anchor,1)
old='''                new AlertDialog.Builder(this).setTitle("Мои коллекции")
                    .setItems(choices,(d,idx)->showCollectionMedia(names.get(idx))).show();'''
assert old in s
s=s.replace(old,'''                new AlertDialog.Builder(this).setTitle("Мои коллекции")
                    .setItems(choices,(d,idx)->showCollectionMedia(names.get(idx)))
                    .setNeutralButton("Управлять",(d,w)->manageCollections(names))
                    .setNegativeButton("Закрыть",null).show();''',1)
pos=s.index("    private void showCollectionMedia(")
s=s[:pos]+'''    private void manageCollections(List<MessageDb.MediaCollection> names){
        String[] labels=new String[names.size()];
        for(int i=0;i<names.size();i++)labels[i]=names.get(i).name+" · "+names.get(i).count+" файлов";
        new AlertDialog.Builder(this).setTitle("Удалить коллекцию")
            .setMessage("Удаляются только ссылки на медиа. Оригиналы и сообщения останутся на месте.")
            .setItems(labels,(d,which)->confirmCollectionDeletion(names.get(which)))
            .setNegativeButton("Отмена",null).show();
    }
    private void confirmCollectionDeletion(MessageDb.MediaCollection collection){
        String origin=activeKey;
        MessageDb originDb=db;
        new AlertDialog.Builder(this).setTitle("Удалить «"+collection.name+"»?")
            .setMessage("Фотографии, видео и тексты не удалятся — только эта подборка.")
            .setPositiveButton("Удалить",(d,w)->worker.execute(()->{
                if(!origin.equals(activeKey))return;
                try{
                    int removed=originDb.deleteCollection(collection.id);
                    runOnUiThread(()->{
                        if(origin.equals(activeKey))toast(removed>0?"Коллекция удалена":"Коллекция уже отсутствует");
                    });
                }catch(Exception ex){runOnUiThread(()->toast("Не удалось удалить коллекцию"));}
            }))
            .setNegativeButton("Отмена",null).show();
    }
'''+s[pos:]
s=s.replace("versionName '0.25-beta'","versionName '0.27-qa'")
s=s.replace('"0.25-qa",Build.VERSION.SDK_INT','"0.27-qa",Build.VERSION.SDK_INT')
main.write_text(s,encoding="utf-8")

db=root/"MessageDb.java"
s=db.read_text(encoding="utf-8")
before='''            // Never leave collection entries linked to re-used attachment IDs.
            d.execSQL("DELETE FROM collection_entries WHERE NOT EXISTS (SELECT 1 FROM attachments a WHERE a.id=collection_entries.attachment_id AND a.path=collection_entries.path)");
'''
assert s.count(before)==2
s=s.replace(before,"",1)  # never reference a table before old-version upgrades create it
anchor='    List<MediaCollection> collections(){'
assert anchor in s
s=s.replace(anchor,'''    /** Only mappings are deleted, never source media or messages. */
    int deleteCollection(long collectionId){
        SQLiteDatabase data=getWritableDatabase();
        data.beginTransaction();
        try{
            data.delete("collection_entries","collection_id=?",
                new String[]{Long.toString(collectionId)});
            int removed=data.delete("media_collections","id=?",
                new String[]{Long.toString(collectionId)});
            data.setTransactionSuccessful();return removed;
        }finally{data.endTransaction();}
    }
'''+anchor,1)
db.write_text(s,encoding="utf-8")

manifest=Path("app/src/main/AndroidManifest.xml")
s=manifest.read_text(encoding="utf-8")
target='        <activity android:name=".MainActivity" android:exported="true">'
assert target in s
s=s.replace(target,'        <activity android:name=".HelpActivity" android:exported="false" />\n'+target,1)
manifest.write_text(s,encoding="utf-8")
build=Path("app/build.gradle")
s=build.read_text(encoding="utf-8")
assert "versionCode 25" in s and "versionName '0.25-beta'" in s
s=s.replace("versionCode 25","versionCode 27").replace("versionName '0.25-beta'","versionName '0.27-qa'")
build.write_text(s,encoding="utf-8")

test=Path("app/src/androidTest/java/org/openarchive/telegram/CollectionsDatabaseTest.java")
s=test.read_text(encoding="utf-8")
anchor='    @Test public void openingVersionEightMigratesWithoutDeletingExistingMessages()throws Exception{'
assert anchor in s
s=s.replace(anchor,'''    @Test public void migrationFromVersionOneDoesNotAccessMissingCollections()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String name="telegram_"+suffix+".db";
        SQLiteDatabase old=ctx.openOrCreateDatabase(name,Context.MODE_PRIVATE,null);
        try{
            old.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT,media TEXT,media_type TEXT)");
            old.execSQL("INSERT INTO messages(id,author,body) VALUES(7,'Test','keep data')");
            old.setVersion(1);
        }finally{old.close();}
        MessageDb current=new MessageDb(ctx,suffix);
        try{
            SQLiteDatabase migrated=current.getWritableDatabase();
            assertEquals(9,migrated.getVersion());
            try(Cursor c=migrated.rawQuery("SELECT body FROM messages WHERE id=7",null)){
                assertTrue(c.moveToFirst());assertEquals("keep data",c.getString(0));
            }
            assertTrue(current.collections().isEmpty());
        }finally{current.close();ctx.deleteDatabase(name);}
    }
    @Test public void collectionDeletionDoesNotRemoveSourceAttachments()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        MessageDb database=new MessageDb(ctx,suffix);
        try{
            SQLiteDatabase sql=database.getWritableDatabase();
            sql.execSQL("INSERT INTO messages(id,body) VALUES (50,'keep')");
            sql.execSQL("INSERT INTO attachments(id,message_id,path,kind) VALUES(20,50,'photos/a.jpg','image')");
            MessageDb.Attachment item=new MessageDb.Attachment();
            item.id=20;item.messageId=50;item.path="photos/a.jpg";item.kind="image";
            long id=database.createCollection("Temporary");
            database.addToCollection(id,item);
            assertEquals(1,database.deleteCollection(id));
            assertEquals(0,database.deleteCollection(id));
            try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM attachments WHERE id=20",null)){
                assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));
            }
        }finally{database.close();ctx.deleteDatabase("telegram_"+suffix+".db");}
    }
'''+anchor,1)
test.write_text(s,encoding="utf-8")
print("Overlay applied: app help, collection deletion, migration fix, tests, version 0.27-qa")
