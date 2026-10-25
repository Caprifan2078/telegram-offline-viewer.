#!/usr/bin/env python3
"""Apply the reviewable offline typography v0.43 delta to a verified v0.42 working tree.
No network, no arbitrary script downloads, and no font redistribution.
"""
from pathlib import Path
root=Path.cwd()
pkg=root/'app/src/main/java/org/openarchive/telegram'
assert (pkg/'MainActivity.java').exists() and (pkg/'HelpContent.java').exists()

def put(name,content):
 f=pkg/name
 if f.exists():
  raise RuntimeError(f'Already exists: {f}')
 f.write_text(content.strip()+'\n', encoding='utf-8')

def replace_once(file,old,new):
 s=file.read_text(encoding='utf-8')
 if s.count(old)!=1:raise RuntimeError(f'Anchor missing or ambiguous in {file}: {old[:100]!r}, occurrences {s.count(old)}')
 file.write_text(s.replace(old,new), encoding='utf-8')

put('FontPolicy.java',r'''package org.openarchive.telegram;
import java.util.Locale;
/** Side-effect-free font validation. Never trust a font merely by extension or MIME type. */
final class FontPolicy {
    static final int MAX_BYTES=5*1024*1024;
    static final String[] KEYS={"system","sans","serif","mono","condensed","casual","light","medium","custom"};
    private FontPolicy(){}
    static boolean known(String key){
        for(String allowed:KEYS)if(allowed.equals(key))return true;
        return false;
    }
    static boolean acceptedName(String name){
        if(name==null || name.length()>250)return false;
        String n=name.toLowerCase(Locale.ROOT);
        return n.endsWith(".ttf")||n.endsWith(".otf");
    }
    static boolean validHeader(byte[] bytes,int count){
        if(bytes==null||count<4)return false;
        int a=bytes[0]&255,b=bytes[1]&255,c=bytes[2]&255,d=bytes[3]&255;
        return (a==0&&b==1&&c==0&&d==0) // TrueType
            ||(a=='O'&&b=='T'&&c=='T'&&d=='O') // OpenType/CFF
            ||(a=='t'&&b=='r'&&c=='u'&&d=='e'); // Apple TrueType
    }
    static boolean withinLimit(long size){return size>4&&size<=MAX_BYTES;}
}
''')
put('FontManager.java',r'''package org.openarchive.telegram;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
/** All font data stays in app-private storage. No storage permission or server needed. */
final class FontManager {
    private static final String PREFS="ta-typography",CHOICE="font-choice";
    static final String[] LABELS={"Как на устройстве · по умолчанию","Sans · современный",
        "Serif · классический","Monospace · печатный","Condensed · компактный",
        "Casual · рукописный","Sans Light · тонкий","Sans Medium · выразительный","Мой импортированный шрифт"};
    private FontManager(){}
    static String choice(Context c){
        String key=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(CHOICE,"system");
        return FontPolicy.known(key)?key:"system";
    }
    static int choiceIndex(Context c){
        String s=choice(c);
        for(int i=0;i<FontPolicy.KEYS.length;i++)if(FontPolicy.KEYS[i].equals(s))return i;
        return 0;
    }
    static void select(Context c,String key){
        if(!FontPolicy.known(key))throw new IllegalArgumentException("Unknown font option");
        if("custom".equals(key)&&!customFile(c).isFile())throw new IllegalStateException("Сначала импортируй шрифт");
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(CHOICE,key).apply();
    }
    static File customFile(Context c){return new File(new File(c.getFilesDir(),"typography"),"user-font.otf");}
    static Typeface resolve(Context c){
        switch(choice(c)){
            case "sans":return Typeface.SANS_SERIF;
            case "serif":return Typeface.SERIF;
            case "mono":return Typeface.MONOSPACE;
            case "condensed":return Typeface.create("sans-serif-condensed",Typeface.NORMAL);
            case "casual":return Typeface.create("casual",Typeface.NORMAL);
            case "light":return Typeface.create("sans-serif-light",Typeface.NORMAL);
            case "medium":return Typeface.create("sans-serif-medium",Typeface.NORMAL);
            case "custom":try{return Typeface.createFromFile(customFile(c));}catch(Exception ignored){return Typeface.DEFAULT;}
            default:return Typeface.DEFAULT; // Enforce the device UI default, including explicitly styled legacy buttons.
        }
    }
    static void importFile(Context c,Uri source) throws IOException {
        if(source==null)throw new IOException("Файл не выбран");
        String name="";
        try(Cursor cur=c.getContentResolver().query(source,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cur!=null&&cur.moveToFirst())name=cur.getString(0);
        }catch(SecurityException e){throw new IOException("Нет доступа к файлу",e);}
        if(!FontPolicy.acceptedName(name))throw new IOException("Выбери шрифт .ttf или .otf");
        File folder=new File(c.getFilesDir(),"typography");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Не удалось создать каталог шрифтов");
        File temp=new File(folder,"incoming-font.tmp");
        try{
            byte[] buf=new byte[8192];long total=0;int count;
            byte[] header=new byte[4];int seen=0;
            try(InputStream in=c.getContentResolver().openInputStream(source);
                FileOutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("Файл недоступен");
                while((count=in.read(buf))!=-1){
                    if(count==0)continue;
                    if(total+count>FontPolicy.MAX_BYTES)throw new IOException("Шрифт больше 5 МБ");
                    for(int i=0;i<count&&seen<4;i++)header[seen++]=buf[i];
                    out.write(buf,0,count);total+=count;
                }
                out.getFD().sync();
            }
            if(!FontPolicy.withinLimit(total)||!FontPolicy.validHeader(header,seen))
                throw new IOException("Некорректный TTF/OTF-файл");
            try{Typeface.createFromFile(temp);}catch(RuntimeException bad){throw new IOException("Android не распознал шрифт",bad);}
            File target=customFile(c);
            try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex){
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            select(c,"custom");
        }finally{if(temp.exists())temp.delete();}
    }
}
''')
put('FontStyler.java',r'''package org.openarchive.telegram;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.util.WeakHashMap;
/** Typography binding to both Activity roots and Dialog windows, including late-loaded views. */
final class FontStyler {
    private FontStyler(){}
    static void bind(Activity owner){bind(owner.getWindow().getDecorView(),owner);}
    static void bind(Dialog dialog){
        if(dialog.getWindow()!=null)bind(dialog.getWindow().getDecorView(),dialog.getContext());
    }
    private static void bind(View decor,Context context){
        Typeface selected=FontManager.resolve(context);
        if(selected==null||decor==null)return;
        final WeakHashMap<TextView,Integer> done=new WeakHashMap<>();
        final Handler ui=new Handler(Looper.getMainLooper());
        final Runnable[] work=new Runnable[1];
        work[0]=()->{decor.setTag(org.openarchive.telegram.R.id.font_style_anchor,Boolean.TRUE);
            walk(decor,selected,done);};
        // Rate-limit traversal. TextViews are processed once per style even on large message pages.
        decor.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            ui.removeCallbacks(work[0]);ui.postDelayed(work[0],85);
        });
        ui.post(work[0]);
    }
    private static void walk(View view,Typeface face,WeakHashMap<TextView,Integer> done){
        if("ta-font-preview".equals(view.getTag()))return;
        if(view instanceof TextView){
            TextView tv=(TextView)view;
            Typeface current=tv.getTypeface();
            int style=current==null?Typeface.NORMAL:current.getStyle();
            Integer previous=done.get(tv);
            if(previous==null||previous!=style){
                tv.setTypeface(Typeface.create(face,style));done.put(tv,style);
            }
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)walk(group.getChildAt(i),face,done);
        }
    }
}
''')
# remove id tag needed; no registered resources ID. simpler no tagging.
f=pkg/'FontStyler.java';s=f.read_text();s=s.replace('decor.setTag(org.openarchive.telegram.R.id.font_style_anchor,Boolean.TRUE);\n            walk(decor,selected,done);','walk(decor,selected,done);');f.write_text(s)
put('FontDialog.java',r'''package org.openarchive.telegram;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
/** Retains the app-wide chosen typography in dialog titles, actions and custom dialog layouts. */
final class FontDialog extends Dialog {
    FontDialog(Context c){super(c);}
    FontDialog(Context c,int theme){super(c,theme);}
    @Override public void show(){super.show();FontStyler.bind(this);}
    static final class Builder extends AlertDialog.Builder {
        Builder(Context c){super(c);}
        Builder(Context c,int theme){super(c,theme);}
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();
            dialog.setOnShowListener(ignored->FontStyler.bind(dialog));
            return dialog;
        }
        @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    }
}
''')

main=pkg/'MainActivity.java'
s=main.read_text(encoding='utf-8')
assert s.count('        super.onCreate(state);')==1
s=s.replace('        super.onCreate(state);','        super.onCreate(state);\n        FontStyler.bind(this);',1)
assert 'private static final int PICK_FOLDER=27' in s
s=s.replace('private static final int PICK_FOLDER=27,','private static final int PICK_FONT=117,PICK_FOLDER=27,',1)
assert s.count('    private void showHelp(){')==1
method=r'''    /** Global typography: one setting for chats, menus, videos and the demo. */
    private void showFontStudio(){
        int selected=FontManager.choiceIndex(this);
        String[] options=FontManager.LABELS;
        new FontDialog.Builder(this).setTitle("Шрифты · оформление всего приложения")
            .setMessage("Системный шрифт выбран по умолчанию. Можно выбрать готовый стиль или добавить свой TTF/OTF. Настройки действуют только внутри Telegram Archive. Для необычных шрифтов убедись, что читается русский текст.")
            .setSingleChoiceItems(options,selected,(dialog,index)->{
                dialog.dismiss();
                if(index==8&&!FontManager.customFile(this).isFile()){
                    new FontDialog.Builder(this).setTitle("Свой шрифт")
                        .setMessage("Сначала выбери локальный .ttf или .otf файл. Он не передаётся в интернет и проверяется перед установкой.")
                        .setPositiveButton("Выбрать файл",(d,w)->pickFontFile())
                        .setNegativeButton("Отмена",null).show();return;
                }
                showFontPreview(index);
            })
            .setNeutralButton("Добавить свой TTF/OTF",(d,w)->pickFontFile())
            .setNegativeButton("Закрыть",null).show();
    }
    private void showFontPreview(int index){
        String key=FontPolicy.KEYS[index];
        TextView sample=new TextView(this);
        sample.setText("Telegram Archive\nСъешь ещё этих мягких французских булок\n1234567890 · Жизнь — это мгновения");
        sample.setTextColor(0xff122639);sample.setTextSize(18);
        sample.setPadding(px(16),px(16),px(16),px(16));
        android.graphics.Typeface chosen;
        switch(key){
            case "serif":chosen=Typeface.SERIF;break;
            case "mono":chosen=Typeface.MONOSPACE;break;
            case "sans":chosen=Typeface.SANS_SERIF;break;
            case "condensed":chosen=Typeface.create("sans-serif-condensed",0);break;
            case "casual":chosen=Typeface.create("casual",0);break;
            case "light":chosen=Typeface.create("sans-serif-light",0);break;
            case "medium":chosen=Typeface.create("sans-serif-medium",0);break;
            case "custom":chosen=Typeface.createFromFile(FontManager.customFile(this));break;
            default:chosen=Typeface.DEFAULT;
        }
        sample.setTag("ta-font-preview");
        sample.setTypeface(chosen);
        new FontDialog.Builder(this).setTitle(FontManager.LABELS[index])
            .setMessage("Предпросмотр. При применении обновятся сообщения, меню, кнопки и справка.")
            .setView(sample).setNegativeButton("Другой шрифт",(d,w)->showFontStudio())
            .setPositiveButton("Применить",(d,w)->{
                FontManager.select(this,key);
                recreate();
            }).show();
    }
    private void pickFontFile(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,PICK_FONT);
    }
    private void handleFontImport(Uri uri){
        toast("Проверяю шрифт…");
        worker.execute(()->{
            String error=null;
            try{FontManager.importFile(this,uri);}catch(Exception e){error=e.getMessage();}
            final String failure=error;
            runOnUiThread(()->{
                if(isFinishing())return;
                if(failure!=null)new FontDialog.Builder(this).setTitle("Шрифт не установлен")
                    .setMessage(failure+". Предыдущий шрифт сохранён.")
                    .setPositiveButton("Понятно",null).show();
                else{toast("Шрифт установлен · Telegram Archive");recreate();}
            });
        });
    }

'''
s=s.replace('    private void showHelp(){',method+'    private void showHelp(){',1)
assert s.count('    @Override protected void onActivityResult(int request,int result,Intent data){')==1
s=s.replace('        super.onActivityResult(request,result,data);\n        if(request==BATCH_TREE_REQUEST)',
'''        super.onActivityResult(request,result,data);
        if(request==PICK_FONT){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null)handleFontImport(data.getData());
            return;
        }
        if(request==BATCH_TREE_REQUEST)''',1)
anchor='        sideMenuItem(items,dialog,pane,"ⓘ  Помощь и инструкция","Пошаговая инструкция внутри приложения",this::showHelp);'
assert s.count(anchor)==1
s=s.replace(anchor,'        sideMenuItem(items,dialog,pane,"Aa  Шрифты · изменить везде","Системный, классика, свой TTF/OTF",this::showFontStudio);\n'+anchor,1)
# typography also in chat settings
assert s.count('    private void settingsMenu(){')==1
s=s.replace('    private void settingsMenu(){new AlertDialog.Builder(this).setTitle("Настройки переписки")',
            '    private void settingsMenu(){new AlertDialog.Builder(this).setTitle("Настройки переписки")',1)
# Dialog wrapper available for every dialog
s=s.replace('new AlertDialog.Builder(', 'new FontDialog.Builder(')
s=s.replace('new Dialog(this,','new FontDialog(this,').replace('new Dialog(this)','new FontDialog(this)')
main.write_text(s,encoding='utf-8')
for name in ['DemoChatActivity.java','RoundPlayerActivity.java']:
 f=pkg/name;src=f.read_text(encoding='utf-8')
 src=src.replace('new AlertDialog.Builder(', 'new FontDialog.Builder(')
 src=src.replace('new Dialog(this,','new FontDialog(this,').replace('new Dialog(this)','new FontDialog(this)')
 # register per-Activity root observation (system choice unchanged)
 if name=='DemoChatActivity.java':
  old='@Override public void onCreate(Bundle state){super.onCreate(state);draw();}'
  assert src.count(old)==1
  src=src.replace(old,'@Override public void onCreate(Bundle state){super.onCreate(state);FontStyler.bind(this);draw();}')
 else:
  import re
  pat=r'(@Override public void onCreate\(Bundle [^)]+\)\{\s*super.onCreate\([^)]+\);)'
  src,cnt=re.subn(pat,r'\1FontStyler.bind(this);',src,count=1)
  if cnt!=1: raise RuntimeError('RoundPlayer onCreate anchor missing')
 f.write_text(src,encoding='utf-8')

helpf=pkg/'HelpContent.java'
entry='        ALL=Collections.unmodifiableList(chapters);'
assert helpf.read_text().count(entry)==1
add=r'''        chapters.add(new Chapter("41. Шрифты: системный, классические и импорт TTF/OTF",
            "Открой боковое меню ☰ → «Шрифты · изменить везде». По умолчанию выбран системный шрифт Android — как в настройках устройства. "
            +"Выбери Sans, Serif, Monospace, Condensed, Casual, Light или Medium и нажми «Применить» после просмотра образца. "
            +"Шрифт меняется во всех основных экранах: сообщения, заголовки, кнопки, ввод, учебный чат, видео и справка. "
            +"Свой шрифт: скачай законно доступный файл .ttf или .otf, например в пиксельном стиле Minecraft, затем открой «Добавить свой TTF/OTF». "
            +"Выбери файл средствами Android. Программа проверяет расширение, заголовок, размер до 5 МБ и возможность его открыть. "
            +"При ошибке старое оформление сохраняется. Для возврата нажми «Как на устройстве». "
            +"Некоторые декоративные шрифты не содержат русских букв: часть символов тогда будет отображаться запасным системным шрифтом. "
            +"Выбранный файл копируется только в закрытую папку Telegram Archive; никакой сервер не используется. "
            +"Настройки шрифта и импортированный файл могут НЕ переноситься старым форматом резервной копии: сохрани исходный файл отдельно. "
            +"Шрифт не устанавливается в Telegram или во все приложения Android. Не загружай шрифты из недоверенных источников."));
'''
replace_once(helpf,entry,add+entry)
catalog=pkg/'HelpCatalog.java'
old='new Shelf("Темы и кнопки","Настройки внешнего вида и новый видеоплеер",12,32,34,36),'
assert catalog.read_text().count(old)==1
replace_once(catalog,old,old+'\n   new Shelf("Шрифты и импорт","Системный, классические, свои TTF и OTF",41),')
# Pure unit tests; no emulator required for policy validation.
test=root/'app/src/test/java/org/openarchive/telegram/FontPolicyTest.java'
test.write_text(r'''package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class FontPolicyTest {
 @Test public void acceptsCaseInsensitiveTtfOtf(){
  assertTrue(FontPolicy.acceptedName("Minecraft.TTF"));
  assertTrue(FontPolicy.acceptedName("my script.otf"));
  assertFalse(FontPolicy.acceptedName("font.ttf.exe"));
  assertFalse(FontPolicy.acceptedName("photo.png"));
  assertFalse(FontPolicy.acceptedName(null));
 }
 @Test public void checksMagicNotJustFileName(){
  assertTrue(FontPolicy.validHeader(new byte[]{0,1,0,0},4));
  assertTrue(FontPolicy.validHeader(new byte[]{'O','T','T','O'},4));
  assertTrue(FontPolicy.validHeader(new byte[]{'t','r','u','e'},4));
  assertFalse(FontPolicy.validHeader(new byte[]{'Z','I','P','!'},4));
  assertFalse(FontPolicy.validHeader(new byte[]{0,1,0,0},3));
 }
 @Test public void sizesAndKnownModes(){
  assertTrue(FontPolicy.withinLimit(FontPolicy.MAX_BYTES));
  assertFalse(FontPolicy.withinLimit(FontPolicy.MAX_BYTES+1L));
  assertFalse(FontPolicy.withinLimit(0));
  assertTrue(FontPolicy.known("system"));
  assertTrue(FontPolicy.known("custom"));
  assertFalse(FontPolicy.known("unknown"));
 }
}
''',encoding='utf-8')
# do not include fonts in build: imported by the user instead.
build=root/'app/build.gradle'
s=build.read_text(encoding='utf-8')
assert "versionCode 42" in s and "versionName '0.42'" in s
s=s.replace('versionCode 42','versionCode 43').replace("versionName '0.42'","versionName '0.43'")
# Avoid replacing the user's existing install: QA stage is isolated, like 0.42.
s=s.replace("applicationIdSuffix '.preview42'","applicationIdSuffix '.preview43'")
build.write_text(s,encoding='utf-8')
print('Applied version 0.43 typography delta: font manager, import guard, 3 Activities, dialogs, help, tests')