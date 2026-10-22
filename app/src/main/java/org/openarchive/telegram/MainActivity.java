package org.openarchive.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.LruCache;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.MediaController;
import android.webkit.MimeTypeMap;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline Telegram-style archive. No INTERNET permission, no uploads or telemetry. */
public class MainActivity extends Activity {
    private static final int PICK_FOLDER=27,PAGE=40,MEDIA_PAGE=30;
    private static final int BG=0xff101928,PANEL=0xff182638,INCOMING=0xff233249,OUTGOING=0xff245a55,
            FG=0xfff4f8ff,SUB=0xffaec0d1,ACCENT=0xff67c2f7,CHIP=0xff304a64,HIGHLIGHT=0xff366a87;
    private static final String[] MEDIA_KEYS={"all","round","voice","image","video","audio","file","gif","sticker"};
    private static final String[] MEDIA_LABELS={"Всё","Кружочки","Голосовые","Фото","Видео","Музыка","Файлы","GIF","Стикеры"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService mediaWorker=Executors.newFixedThreadPool(2);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> thumbs=new LruCache<String,Bitmap>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap b){return b.getByteCount();}
    };
    private final LruCache<String,String> durations=new LruCache<>(250);
    private MessageDb db;private DocumentFolder folder;
    private LinearLayout base,list,filterBar,bottomNav,searchArea,toolArea;
    private ScrollView scroll;private TextView title,subtitle,status,pageLabel;
    private EditText searchInput,authorInput;private CheckBox mediaOnly;
    private Button backPage,nextPage,quickButton,deepButton,folderButton,backButton;
    private String screen="chat",mediaKind="all",q="",byAuthor="",mine="",returnScreen="";
    private boolean deep=false,onlyMedia=false,importing=false;
    private int chatOffset=0,searchOffset=0,mediaOffset=0,favOffset=0,total=0,loadToken=0;
    private long highlightId=-1;
    private String info="";
    private MediaPlayer player;private String playingPath="";private TextureView activeTexture;
    private Runnable progressUpdater;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        db=new MessageDb(this);
        mine=getPreferences(MODE_PRIVATE).getString("mine","");
        String old=getPreferences(MODE_PRIVATE).getString("tree",null);
        if(old!=null)try{folder=new DocumentFolder(getContentResolver(),Uri.parse(old));}catch(Exception ignored){}
        createUi();showPage();
    }
    private int px(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(radius));return d;}
    private GradientDrawable gradient(int first,int second,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});d.setCornerRadius(px(radius));return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(true);return t;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams p(int w,int h){return new LinearLayout.LayoutParams(w<0?w:px(w),h<0?h:px(h));}
    private Button btn(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(12);b.setTextColor(FG);b.setPadding(px(8),0,px(8),0);b.setBackground(shape(PANEL,13));return b;}
    private void space(ViewGroup g,int height){g.addView(new View(this),new ViewGroup.LayoutParams(1,px(height)));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private void createUi(){
        base=column();base.setBackgroundColor(BG);base.setPadding(px(9),px(5),px(9),px(6));setContentView(base);
        LinearLayout head=row();base.addView(head,p(-1,55));
        TextView logo=text("◉",32,ACCENT);head.addView(logo,p(43,-2));
        LinearLayout headings=column();head.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        title=text("Telegram Archive",19,FG);title.setTypeface(null,Typeface.BOLD);headings.addView(title);
        subtitle=text("Личный архив · офлайн",11,SUB);headings.addView(subtitle);
        Button settings=btn("⚙");head.addView(settings,p(39,40));settings.setOnClickListener(v->identityDialog());
        folderButton=btn("📁");head.addView(folderButton,p(43,40));folderButton.setOnClickListener(v->chooseFolder());
        bottomNav=row();base.addView(bottomNav,p(-1,51));
        addTab("💬 Чат","chat");addTab("▦ Медиа","media");addTab("★ Избранное","favorites");addTab("⌕ Поиск","search");
        toolArea=column();base.addView(toolArea,p(-1,-2));
        filterBar=column();toolArea.addView(filterBar,p(-1,-2));
        searchArea=column();toolArea.addView(searchArea,p(-1,-2));
        status=text("",11,SUB);status.setPadding(px(8),px(4),px(8),px(6));status.setMaxLines(3);base.addView(status,p(-1,-2));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        base.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        list=column();list.setPadding(px(3),px(6),px(3),px(14));scroll.addView(list);
        LinearLayout bottom=row();base.addView(bottom,p(-1,52));
        backPage=btn("‹ Назад");nextPage=btn("Далее ›");pageLabel=text("",12,SUB);pageLabel.setGravity(Gravity.CENTER);
        bottom.addView(backPage,p(95,44));bottom.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));bottom.addView(nextPage,p(95,44));
        backPage.setOnClickListener(v->{setOffset(Math.max(0,getOffset()-getLimit()));showPage();});
        nextPage.setOnClickListener(v->{setOffset(getOffset()+getLimit());showPage();});
        configureBars();
    }
    private void addTab(String label,String key){
        TextView t=text(label,12,SUB);t.setGravity(Gravity.CENTER);t.setTag(key);
        bottomNav.addView(t,new LinearLayout.LayoutParams(0,px(45),1));
        t.setOnClickListener(v->{stopPlayback();screen=key;returnScreen="";highlightId=-1;showPage();});
    }
    private void refreshTabs(){
        for(int i=0;i<bottomNav.getChildCount();i++){
            TextView t=(TextView)bottomNav.getChildAt(i);boolean selected=screen.equals(t.getTag());
            t.setBackground(shape(selected?CHIP:PANEL,13));t.setTextColor(selected?FG:SUB);
        }
        subtitle.setText(screen.equals("chat")?"История сообщений · локально":screen.equals("media")?"Фото, видео, голосовые и файлы":screen.equals("favorites")?"Твои сохранённые сообщения":"Найди любое сообщение");
    }
    private void configureBars(){
        filterBar.removeAllViews();searchArea.removeAllViews();
        if("media".equals(screen)){
            HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout chips=row();hsv.addView(chips);
            for(int i=0;i<MEDIA_KEYS.length;i++){
                final String key=MEDIA_KEYS[i];TextView c=text(MEDIA_LABELS[i],12,FG);c.setGravity(Gravity.CENTER);
                c.setPadding(px(12),px(8),px(12),px(8));c.setBackground(shape(key.equals(mediaKind)?HIGHLIGHT:PANEL,14));
                LinearLayout.LayoutParams cp=p(-2,37);cp.setMargins(px(2),px(2),px(4),px(2));chips.addView(c,cp);
                c.setOnClickListener(v->{mediaKind=key;mediaOffset=0;showPage();});
            }
            filterBar.addView(hsv,p(-1,44));
        }
        if("chat".equals(screen)){
            LinearLayout actions=row();
            Button date=btn("📅 Перейти к дате");actions.addView(date,new LinearLayout.LayoutParams(0,px(42),1));date.setOnClickListener(v->chooseDate());
            if(!returnScreen.isEmpty()){
                backButton=btn("↩ Назад к "+("media".equals(returnScreen)?"медиа":"поиску"));
                actions.addView(backButton,p(-2,42));backButton.setOnClickListener(v->restoreReturn());
            }
            filterBar.addView(actions,p(-1,46));
        }
        if("search".equals(screen)){
            searchInput=new EditText(this);searchInput.setText(q);searchInput.setTextSize(16);searchInput.setSingleLine(true);
            searchInput.setTextColor(FG);searchInput.setHintTextColor(SUB);searchInput.setHint("Слово, фраза или часть слова");
            searchInput.setPadding(px(14),px(3),px(14),px(3));searchInput.setBackground(shape(PANEL,12));
            searchArea.addView(searchInput,p(-1,48));
            LinearLayout controls=row();quickButton=btn("⚡ Быстрый поиск");deepButton=btn("⌕ Глубокий поиск");
            controls.addView(quickButton,new LinearLayout.LayoutParams(0,px(43),1));controls.addView(deepButton,new LinearLayout.LayoutParams(0,px(43),1));
            searchArea.addView(controls,p(-1,49));
            quickButton.setBackground(shape(!deep?HIGHLIGHT:PANEL,10));deepButton.setBackground(shape(deep?HIGHLIGHT:PANEL,10));
            quickButton.setOnClickListener(v->{deep=false;applySearch();});
            deepButton.setOnClickListener(v->{deep=true;applySearch();});
            if(deep){
                authorInput=new EditText(this);authorInput.setText(byAuthor);authorInput.setSingleLine(true);
                authorInput.setTextColor(FG);authorInput.setHintTextColor(SUB);authorInput.setTextSize(13);
                authorInput.setHint("Отправитель (необязательно)");authorInput.setBackground(shape(PANEL,10));
                authorInput.setPadding(px(12),px(4),px(12),px(4));searchArea.addView(authorInput,p(-1,39));
                mediaOnly=new CheckBox(this);mediaOnly.setText("Только сообщения с медиа");mediaOnly.setTextColor(FG);mediaOnly.setTextSize(12);
                mediaOnly.setChecked(onlyMedia);searchArea.addView(mediaOnly,p(-1,39));
                TextView hint=text("Глубокий поиск ищет точное сочетание букв, даже внутри слова; может работать медленнее.",11,SUB);
                hint.setPadding(px(7),0,0,px(6));searchArea.addView(hint,p(-1,-2));
            }else{
                TextView hint=text("Быстрый поиск по словам и их началам. Нажми на результат, чтобы перейти в чат.",11,SUB);
                hint.setPadding(px(7),0,0,px(4));searchArea.addView(hint,p(-1,-2));
            }
            searchInput.setOnEditorActionListener((v,action,event)->{applySearch();return true;});
        }
    }
    private void applySearch(){
        if(searchInput!=null)q=searchInput.getText().toString().trim();
        if(authorInput!=null&&deep)byAuthor=authorInput.getText().toString().trim();
        if(mediaOnly!=null&&deep)onlyMedia=mediaOnly.isChecked();
        searchOffset=0;configureBars();hideKeyboard();showPage();
    }
    private void hideKeyboard(){try{((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(base.getWindowToken(),0);}catch(Exception ignored){}}
    private void identityDialog(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(mine);input.setHint("Имя в переписке Telegram");
        LinearLayout wrap=column();wrap.setPadding(px(20),px(5),px(20),0);wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Как тебя зовут в экспорте?")
                .setMessage("Укажи имя отправителя точно как в HTML, чтобы твои сообщения были справа и выделялись цветом.")
                .setView(wrap).setNegativeButton("Отмена",null)
                .setPositiveButton("Сохранить",(d,w)->{
                    mine=input.getText().toString().trim();getPreferences(MODE_PRIVATE).edit().putString("mine",mine).apply();showPage();
                }).show();
    }
    private void chooseDate(){
        Calendar c=Calendar.getInstance();
        new DatePickerDialog(this,(view,y,m,d)->worker.execute(()->{
            int pos=db.positionForDate(y,m+1,d);
            runOnUiThread(()->{if(pos>=0){screen="chat";chatOffset=Math.max(0,pos-5);highlightId=-1;returnScreen="";showPage();}
                else toast("На эту дату сообщений нет");});
        }),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void chooseFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_FOLDER);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_FOLDER||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(Exception e){status.setText("Нет доступа к папке: "+e.getMessage());return;}
        getPreferences(MODE_PRIVATE).edit().putString("tree",uri.toString()).apply();
        folder=new DocumentFolder(getContentResolver(),uri);
        new AlertDialog.Builder(this).setTitle("Импортировать экспорт Telegram?")
                .setMessage("Будут проиндексированы все messages*.html. Фото, видео и голосовые останутся в этой папке. Текущие сообщения заменятся только после успешного импорта. Избранное по возможности сохранится.")
                .setPositiveButton("Импортировать",(d,w)->importArchive()).setNegativeButton("Отмена",null).show();
    }
    private void importArchive(){
        if(folder==null||importing)return;stopPlayback();importing=true;folderButton.setEnabled(false);status.setText("Создаю локальную базу, не копируя медиа…");
        list.removeAllViews();DocumentFolder current=folder;
        worker.execute(()->{
            try{TelegramImporter.importFiles(getContentResolver(),current,db,s->runOnUiThread(()->status.setText(s)));info="Импорт завершён";}
            catch(Exception e){info="Ошибка импорта: "+e.getMessage();}
            runOnUiThread(()->{importing=false;folderButton.setEnabled(true);status.setText(info);chatOffset=0;mediaOffset=0;searchOffset=0;favOffset=0;showPage();});
        });
    }
    private int getOffset(){switch(screen){case "media":return mediaOffset;case "search":return searchOffset;case "favorites":return favOffset;default:return chatOffset;}}
    private int getLimit(){return screen.equals("media")?MEDIA_PAGE:PAGE;}
    private void setOffset(int x){switch(screen){case "media":mediaOffset=x;break;case "search":searchOffset=x;break;case "favorites":favOffset=x;break;default:chatOffset=x;}}
    private void showPage(){
        if(importing)return;stopPlayback();refreshTabs();configureBars();int token=++loadToken;
        final String tab=screen,kind=mediaKind,search=q,who=byAuthor;
        final int requested=getOffset();final boolean deepMode=deep,hasMedia=onlyMedia;
        backPage.setEnabled(false);nextPage.setEnabled(false);status.setText("Загружаю…");
        worker.execute(()->{
            try{
                boolean gallery="media".equals(tab),find="search".equals(tab),favs="favorites".equals(tab);
                int count=gallery?db.mediaCount(kind):db.count(find?search:"",find&&deepMode,find&&deepMode?who:"",find&&deepMode&&hasMedia,favs);
                int pos=count==0?0:Math.min(requested,Math.max(0,count-1));
                List<MessageDb.Attachment> attachments=gallery?db.mediaPage(kind,pos,MEDIA_PAGE):null;
                List<MessageDb.Item> messages=gallery?null:db.page(pos,PAGE,find?search:"",find&&deepMode,find&&deepMode?who:"",find&&deepMode&&hasMedia,favs);
                Map<Long,List<MessageDb.Attachment>> links=new HashMap<>();
                if(messages!=null)for(MessageDb.Item m:messages)links.put(m.id,db.attachmentsForMessage(m.id));
                runOnUiThread(()->{
                    if(isFinishing()||token!=loadToken)return;
                    total=count;setOffset(pos);list.removeAllViews();
                    if(gallery)renderMedia(attachments);else renderMessages(messages,links,find);
                    int shown=gallery?attachments.size():messages.size();
                    pageLabel.setText(count==0?"Нет данных":(pos+1)+"–"+(pos+shown)+" / "+count);
                    backPage.setEnabled(pos>0);nextPage.setEnabled(pos+shown<count);
                    if(count==0)status.setText(gallery?"Таких медиа пока нет. Попробуй повторный импорт исходных HTML.":
                            "Ничего не найдено — попробуй другой запрос или выбери папку.");
                    else status.setText("media".equals(tab)?"Нажми — воспроизвести · удерживай — действия":
                            "search".equals(tab)?"Найдено "+count+" · нажми «К сообщению»":
                            "favorites".equals(tab)?"Твои избранные сообщения": "История · удерживай сообщение для действий");
                });
            }catch(Exception e){runOnUiThread(()->{if(token==loadToken)status.setText("Не удалось прочитать базу: "+e.getMessage());});}
        });
    }
    private String clip(String s,int max){if(s==null)return "";return s.length()>max?s.substring(0,max)+"…":s;}
    private String day(String s){if(s==null)return "";if(s.length()<10)return s;return s.substring(0,10);}
    private String clock(String s){if(s==null)return "";
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\b([0-2]?\\d:[0-5]\\d)(?::[0-5]\\d)?\\b").matcher(s);
        return m.find()?m.group(1):clip(s,22);
    }
    private void dateSeparator(String date){
        TextView d=text(date.isEmpty()?"Дата неизвестна":date,11,SUB);d.setGravity(Gravity.CENTER);
        d.setPadding(px(12),px(7),px(12),px(7));d.setBackground(shape(0xff23384b,14));
        LinearLayout r=row();r.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=p(-1,38);rp.topMargin=px(10);list.addView(r,rp);r.addView(d);
    }
    private CharSequence highlighted(String body,String find){
        if(body==null)return "";if(find==null||find.trim().isEmpty())return body;
        SpannableString span=new SpannableString(body);String hay=body.toLowerCase(Locale.ROOT);
        ArrayList<String> needles=new ArrayList<>();
        if(deep)needles.add(find.toLowerCase(Locale.ROOT));
        else for(String token:find.split("[^\\p{L}\\p{N}]+"))if(!token.isEmpty())needles.add(token.toLowerCase(Locale.ROOT));
        for(String needle:needles){int from=0,n=0,at;
            while(!needle.isEmpty()&&(at=hay.indexOf(needle,from))>=0&&n++<30){
                span.setSpan(new BackgroundColorSpan(0xffa37528),at,at+needle.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                from=at+needle.length();
            }
        }return span;
    }
    private void renderMessages(List<MessageDb.Item> messages,Map<Long,List<MessageDb.Attachment>> files,boolean results){
        if(messages.isEmpty())return;
        String last="";View focus=null;
        for(MessageDb.Item m:messages){
            String date=day(m.date);if(!date.equals(last)){dateSeparator(date);last=date;}
            boolean own=!mine.isEmpty()&&mine.equalsIgnoreCase(m.author==null?"":m.author.trim());
            LinearLayout line=row();line.setGravity(own?Gravity.RIGHT:Gravity.LEFT);
            LinearLayout.LayoutParams linep=p(-1,-2);linep.setMargins(px(2),px(2),px(2),px(5));list.addView(line,linep);
            if(!own){TextView avatar=text(initial(m.author),14,FG);avatar.setTypeface(null,Typeface.BOLD);
                avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(0xff456884,100));
                LinearLayout.LayoutParams ap=p(32,32);ap.rightMargin=px(7);line.addView(avatar,ap);}
            LinearLayout bubble=column();bubble.setPadding(px(13),px(8),px(13),px(9));
            bubble.setBackground(gradient(m.id==highlightId?0xff477c91:own?OUTGOING:INCOMING,m.id==highlightId?0xff33576b:own?0xff1f4745:0xff1c2b41,16));
            LinearLayout.LayoutParams bp=p(-2,-2);line.addView(bubble,bp);
            int maxWidth=getResources().getDisplayMetrics().widthPixels-px(93);
            if(maxWidth<px(190))maxWidth=px(240);final int mw=maxWidth;
            bubble.setOnLongClickListener(v->{messageActions(m);return true;});
            String author=m.author==null||m.author.isEmpty()?"Неизвестный отправитель":m.author;
            TextView from=text(author,12,own?0xff9fe1c5:ACCENT);from.setMaxWidth(mw);from.setTypeface(null,Typeface.BOLD);bubble.addView(from);
            if(m.body!=null&&!m.body.isEmpty()){
                TextView body=text("",15,FG);body.setMaxWidth(mw);body.setText(highlighted(m.body,results?q:""));
                body.setPadding(0,px(5),0,px(4));body.setLineSpacing(px(2),1.0f);bubble.addView(body);
            }
            List<MessageDb.Attachment> attachments=files.get(m.id);
            if(attachments!=null)for(MessageDb.Attachment a:attachments)bubble.addView(mediaTile(a,false),p(-2,-2));
            LinearLayout footer=row();footer.setGravity(Gravity.RIGHT);
            TextView time=text(clock(m.date)+"  "+(m.favorite?"★":"☆"),11,m.favorite?0xffffdf94:SUB);
            footer.addView(time);bubble.addView(footer);
            time.setOnClickListener(v->toggleStar(m.id));
            if(results){
                TextView open=text("↗ К сообщению в чате",12,ACCENT);open.setPadding(0,px(8),0,px(2));bubble.addView(open);
                open.setOnClickListener(v->jump(m.id,"search"));
                bubble.setOnClickListener(v->jump(m.id,"search"));
            }
            if(m.id==highlightId)focus=line;
        }
        final View target=focus;
        scroll.post(()->scroll.scrollTo(0,target==null?0:Math.max(0,target.getTop()-px(95))));
    }
    private String initial(String s){if(s==null||s.isEmpty())return "?";return s.substring(0,1).toUpperCase(Locale.ROOT);}
    private void messageActions(MessageDb.Item m){
        String[] options={m.favorite?"Убрать из избранного":"★ Добавить в избранное","Скопировать текст","Перейти к сообщению"};
        new AlertDialog.Builder(this).setTitle("Сообщение · "+clock(m.date)).setItems(options,(dia,which)->{
            if(which==0)toggleStar(m.id);
            else if(which==1){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Telegram message",m.body==null?"":m.body));toast("Текст скопирован");}
            else jump(m.id,screen.equals("search")?"search":screen.equals("favorites")?"favorites":"");
        }).show();
    }
    private void toggleStar(long id){worker.execute(()->{
        boolean on=db.toggleFavorite(id);runOnUiThread(()->{toast(on?"★ Добавлено в избранное":"Убрано из избранного");showPage();});
    });}
    private void renderMedia(List<MessageDb.Attachment> media){
        if(media.isEmpty())return;
        boolean grid=mediaKind.equals("image")||mediaKind.equals("video")||mediaKind.equals("gif")||mediaKind.equals("sticker");
        if(grid){
            GridLayout gallery=new GridLayout(this);gallery.setColumnCount(3);list.addView(gallery,p(-1,-2));
            int screenWidth=getResources().getDisplayMetrics().widthPixels;
            int cell=(screenWidth-px(40))/3;
            for(MessageDb.Attachment a:media){
                FrameLayout tile=new FrameLayout(this);tile.setBackground(shape(PANEL,9));tile.setClipToOutline(true);
                GridLayout.LayoutParams t=new GridLayout.LayoutParams();t.width=cell-px(4);t.height=cell-px(4);t.setMargins(px(2),px(2),px(2),px(2));gallery.addView(tile,t);
                ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                tile.addView(image,new FrameLayout.LayoutParams(-1,-1));loadThumbnail(a.path,a.kind,image);
                if(a.kind.equals("video")){TextView play=text("▶",25,FG);play.setGravity(Gravity.CENTER);tile.addView(play,new FrameLayout.LayoutParams(-1,-1));}
                tile.setOnClickListener(v->openMedia(a));tile.setOnLongClickListener(v->{mediaActions(a);return true;});
            }
        }else for(MessageDb.Attachment a:media){
            LinearLayout item=row();item.setPadding(px(11),px(10),px(10),px(10));item.setBackground(shape(PANEL,17));
            LinearLayout.LayoutParams ip=p(-1,-2);ip.setMargins(px(1),px(4),px(1),px(5));list.addView(item,ip);
            View tile=mediaTile(a,true);item.addView(tile,p(-2,-2));
            LinearLayout desc=column();desc.setPadding(px(10),0,0,0);item.addView(desc,new LinearLayout.LayoutParams(0,-2,1));
            TextView name=text(mediaLabel(a.kind),14,FG);name.setTypeface(null,Typeface.BOLD);desc.addView(name);
            TextView date=text(day(a.date)+" · "+clock(a.date),12,SUB);desc.addView(date);
            if(a.author!=null&&!a.author.isEmpty()){TextView author=text(clip(a.author,25),11,SUB);desc.addView(author);}
            TextView go=text("↗ Перейти в чат",12,ACCENT);go.setPadding(0,px(11),0,0);desc.addView(go);
            go.setOnClickListener(v->jump(a.messageId,"media"));
            item.setOnLongClickListener(v->{mediaActions(a);return true;});
        }
        scroll.post(()->scroll.scrollTo(0,0));
    }
    private String mediaLabel(String k){switch(k){case "round":return "Видеокружочек";case "voice":return "Голосовое";case "video":return "Видео";case "audio":return "Аудио / музыка";case "image":return "Фото";case "gif":return "GIF";case "sticker":return "Стикер";default:return "Файл";}}
    private View mediaTile(MessageDb.Attachment a,boolean gallery){
        if(a.kind.equals("round"))return circle(a,gallery?88:118);
        if(a.kind.equals("voice")||a.kind.equals("audio"))return voice(a,gallery);
        if(a.kind.equals("image")||a.kind.equals("gif")||a.kind.equals("sticker")||a.kind.equals("video")){
            FrameLayout box=new FrameLayout(this);int size=gallery?84:190;
            box.setBackground(shape(CHIP,11));box.setClipToOutline(true);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(image,new FrameLayout.LayoutParams(-1,-1));loadThumbnail(a.path,a.kind,image);
            if(a.kind.equals("video")){TextView play=text("▶",27,FG);play.setGravity(Gravity.CENTER);box.addView(play,new FrameLayout.LayoutParams(-1,-1));}
            box.setOnClickListener(v->openMedia(a));box.setOnLongClickListener(v->{mediaActions(a);return true;});
            FrameLayout outer=new FrameLayout(this);outer.addView(box,new FrameLayout.LayoutParams(px(size),px(gallery?84:140)));return outer;
        }
        TextView file=text("📎 "+clip(filename(a.path),gallery?18:33),13,ACCENT);file.setPadding(px(8),px(10),px(8),px(10));
        file.setBackground(shape(CHIP,12));file.setOnClickListener(v->openMedia(a));
        file.setOnLongClickListener(v->{mediaActions(a);return true;});return file;
    }
    private String filename(String path){int p=path.lastIndexOf('/');return p<0?path:path.substring(p+1);}
    private View circle(MessageDb.Attachment a,int size){
        FrameLayout container=new FrameLayout(this);container.setBackground(shape(0xff31526c,100));
        ViewOutlineProvider round=new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}};
        container.setOutlineProvider(round);container.setClipToOutline(true);
        ImageView thumbnail=new ImageView(this);thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        container.addView(thumbnail,new FrameLayout.LayoutParams(-1,-1));loadThumbnail(a.path,"round",thumbnail);
        TextView overlay=text("▶",size>100?37:27,FG);overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(0x42000000);container.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        container.setOnClickListener(v->startInlineRound(a.path,container,thumbnail,overlay));
        container.setOnLongClickListener(v->{mediaActions(a);return true;});
        LinearLayout wrap=column();wrap.setGravity(Gravity.CENTER_HORIZONTAL);wrap.addView(container,p(size,size));
        TextView duration=text("00:00",11,SUB);duration.setGravity(Gravity.CENTER);wrap.addView(duration,p(size,23));
        loadDuration(a.path,duration);return wrap;
    }
    private View voice(MessageDb.Attachment a,boolean gallery){
        LinearLayout outer=column();outer.setBackground(shape(CHIP,14));outer.setPadding(px(8),px(5),px(8),px(5));
        LinearLayout inner=row();outer.addView(inner,p(gallery?98:218,47));
        TextView play=text("▶",23,FG);play.setGravity(Gravity.CENTER);inner.addView(play,p(32,42));
        LinearLayout details=column();details.setPadding(px(5),0,0,0);inner.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        TextView bars=text("▂▆▃▇▄▅▂▆▃▄▇▂▅",gallery?11:15,ACCENT);bars.setSingleLine(true);details.addView(bars);
        TextView duration=text("00:00",11,SUB);details.addView(duration);loadDuration(a.path,duration);
        play.setOnClickListener(v->startAudio(a.path,play,duration));
        outer.setOnLongClickListener(v->{mediaActions(a);return true;});return outer;
    }
    private void mediaActions(MessageDb.Attachment a){
        String[] names={"▶ Воспроизвести / открыть","↗ Перейти к сообщению","☆ Отметить сообщение","⛶ Открыть полностью"};
        new AlertDialog.Builder(this).setTitle(mediaLabel(a.kind)+" · "+day(a.date)).setItems(names,(dlg,which)->{
            if(which==0||which==3)openMedia(a);
            if(which==1)jump(a.messageId,"media");
            if(which==2)toggleStar(a.messageId);
        }).show();
    }
    private void jump(long id,String source){
        status.setText("Перехожу к сообщению…");
        worker.execute(()->{
            int at=db.hasMessage(id)?db.positionFor(id):-1;
            runOnUiThread(()->{
                if(at<0){toast("Сообщение не найдено");return;}
                returnScreen=source;screen="chat";chatOffset=Math.max(0,at-7);highlightId=id;showPage();
            });
        });
    }
    private void restoreReturn(){
        if(returnScreen.isEmpty())return;
        screen=returnScreen;returnScreen="";highlightId=-1;showPage();
    }
    @Override public void onBackPressed(){if(!returnScreen.isEmpty()){restoreReturn();return;}super.onBackPressed();}
    private void resolveMedia(String path,java.util.function.Consumer<Uri> success){
        if(folder==null){toast("Сначала выбери папку с экспортом");return;}
        DocumentFolder current=folder;
        mediaWorker.execute(()->{
            Uri result=null;try{result=current.resolve(path);}catch(Exception ignored){}
            Uri uri=result;runOnUiThread(()->{
                if(uri==null){status.setText("Файл не найден: "+path);toast("Медиа не найдено в выбранной папке");}
                else success.accept(uri);
            });
        });
    }
    private void openMedia(MessageDb.Attachment a){
        if(a.kind.equals("video")||a.kind.equals("round"))resolveMedia(a.path,this::fullVideo);
        else if(a.kind.equals("voice")||a.kind.equals("audio"))resolveMedia(a.path,uri->external(uri,a.path));
        else resolveMedia(a.path,uri->external(uri,a.path));
    }
    private void external(Uri uri,String path){
        String ext=path.contains(".")?path.substring(path.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
        String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);if(mime==null)mime="application/octet-stream";
        Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(uri,mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(Intent.createChooser(intent,"Открыть файл"));}
        catch(Exception e){toast("Не найдено приложение для открытия файла");}
    }
    private void fullVideo(Uri uri){
        stopPlayback();Dialog d=new Dialog(this);
        LinearLayout layout=column();layout.setBackgroundColor(Color.BLACK);
        VideoView video=new VideoView(this);layout.addView(video,new LinearLayout.LayoutParams(-1,0,1));
        Button external=btn("Открыть в другом плеере");layout.addView(external,p(-1,51));
        external.setOnClickListener(v->{external(uri,"media.mp4");d.dismiss();});
        d.setContentView(layout);d.show();Window w=d.getWindow();if(w!=null)w.setLayout(-1,px(500));
        MediaController controller=new MediaController(this);controller.setAnchorView(video);video.setMediaController(controller);
        video.setOnPreparedListener(mp->{video.start();controller.show(2500);});
        video.setOnErrorListener((mp,what,extra)->{toast("Не удалось запустить видео. Попробуй другой плеер.");return true;});
        d.setOnDismissListener(x->video.stopPlayback());video.setVideoURI(uri);video.requestFocus();
    }
    private void stopPlayback(){
        if(progressUpdater!=null){handler.removeCallbacks(progressUpdater);progressUpdater=null;}
        if(player!=null){try{player.stop();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
        activeTexture=null;playingPath="";
    }
    /** TextureView rather than SurfaceView allows a genuinely round clipping mask. */
    private void startInlineRound(String path,FrameLayout holder,ImageView poster,TextView overlay){
        if(player!=null&&path.equals(playingPath)){
            try{if(player.isPlaying()){player.pause();overlay.setVisibility(View.VISIBLE);}else{player.start();overlay.setVisibility(View.GONE);}}catch(Exception ignored){}
            return;
        }
        stopPlayback();resolveMedia(path,uri->{
            playingPath=path;TextureView texture=new TextureView(this);activeTexture=texture;
            FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,-1);
            holder.addView(texture,1,params);overlay.bringToFront();
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int w,int h){
                    if(!path.equals(playingPath))return;
                    try{
                        MediaPlayer p=new MediaPlayer();player=p;Surface screenSurface=new Surface(surface);
                        p.setSurface(screenSurface);screenSurface.release();p.setDataSource(MainActivity.this,uri);
                        p.setLooping(true);
                        p.setOnPreparedListener(mp->{if(mp!=player)return;
                            Matrix transform=new Matrix();float vw=Math.max(1,mp.getVideoWidth()),vh=Math.max(1,mp.getVideoHeight());
                            float scale=Math.max(w/vw,h/vh);float sx=vw*scale/w,sy=vh*scale/h;
                            transform.setScale(sx,sy,w/2f,h/2f);texture.setTransform(transform);
                            overlay.setVisibility(View.GONE);poster.setVisibility(View.INVISIBLE);mp.start();
                        });
                        p.setOnErrorListener((mp,what,extra)->{toast("Видео не запустилось. Удержи кружочек → открой полностью.");stopPlayback();overlay.setVisibility(View.VISIBLE);return true;});
                        p.prepareAsync();
                    }catch(Exception e){stopPlayback();overlay.setVisibility(View.VISIBLE);toast("Ошибка воспроизведения");}
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s){if(activeTexture==texture)stopPlayback();return true;}
                @Override public void onSurfaceTextureUpdated(SurfaceTexture s){}
            });
        });
    }
    private void startAudio(String path,TextView play,TextView timer){
        if(player!=null&&path.equals(playingPath)){
            try{if(player.isPlaying()){player.pause();play.setText("▶");}else{player.start();play.setText("❚❚");}}catch(Exception ignored){}
            return;
        }
        stopPlayback();resolveMedia(path,uri->{
            try{
                MediaPlayer p=new MediaPlayer();player=p;playingPath=path;p.setDataSource(this,uri);
                p.setOnPreparedListener(mp->{if(mp!=player)return;mp.start();play.setText("❚❚");
                    progressUpdater=new Runnable(){@Override public void run(){
                        if(mp!=player)return;
                        try{int seconds=mp.getCurrentPosition()/1000;
                            int len=mp.getDuration()/1000;
                            timer.setText(formatTime(seconds)+" / "+formatTime(len));
                            handler.postDelayed(this,500);
                        }catch(Exception ignored){}
                    }};handler.post(progressUpdater);
                });
                p.setOnCompletionListener(mp->{play.setText("▶");stopPlayback();});
                p.setOnErrorListener((mp,what,extra)->{toast("Не удалось воспроизвести аудио");stopPlayback();play.setText("▶");return true;});
                p.prepareAsync();
            }catch(Exception e){stopPlayback();toast("Аудиофайл недоступен");}
        });
    }
    private String formatTime(int s){return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    private void loadDuration(String path,TextView target){
        String cached=durations.get(path);if(cached!=null){target.setText(cached);return;}
        if(folder==null)return;DocumentFolder current=folder;int token=loadToken;
        mediaWorker.execute(()->{
            String time="00:00";
            try{Uri uri=current.resolve(path);if(uri!=null){MediaMetadataRetriever reader=new MediaMetadataRetriever();
                try{reader.setDataSource(this,uri);String raw=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if(raw!=null)time=formatTime((int)(Long.parseLong(raw)/1000));}finally{reader.release();}
            }}catch(Exception ignored){}
            String result=time;durations.put(path,result);
            runOnUiThread(()->{if(token==loadToken)target.setText(result);});
        });
    }
    private void loadThumbnail(String path,String kind,ImageView image){
        Bitmap cached=thumbs.get(path);if(cached!=null&&!cached.isRecycled()){image.setImageBitmap(cached);return;}
        if(folder==null)return;DocumentFolder current=folder;int token=loadToken;
        mediaWorker.execute(()->{
            Bitmap bitmap=null;
            try{
                Uri uri=current.resolve(path);
                if(uri!=null){
                    boolean picture=kind.equals("image")||kind.equals("gif")||kind.equals("sticker");
                    if(picture){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
                        try(InputStream is=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(is,null,o);}
                        o.inJustDecodeBounds=false;o.inSampleSize=Math.max(1,Math.max(o.outWidth/230,o.outHeight/230));
                        try(InputStream is=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(is,null,o);}
                    }else{MediaMetadataRetriever r=new MediaMetadataRetriever();
                        try{r.setDataSource(this,uri);bitmap=r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);}finally{r.release();}
                    }
                }
            }catch(Exception ignored){}
            if(bitmap==null)return;
            if(bitmap.getWidth()>270||bitmap.getHeight()>270){
                float scale=Math.min(270f/bitmap.getWidth(),270f/bitmap.getHeight());
                Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),Math.max(1,(int)(bitmap.getHeight()*scale)),true);
                if(small!=bitmap)bitmap.recycle();bitmap=small;
            }
            Bitmap result=bitmap;
            runOnUiThread(()->{if(!isFinishing()&&token==loadToken){thumbs.put(path,result);image.setImageBitmap(result);}else result.recycle();});
        });
    }
    @Override protected void onPause(){super.onPause();stopPlayback();}
    @Override protected void onDestroy(){stopPlayback();worker.execute(()->db.close());worker.shutdown();mediaWorker.shutdown();super.onDestroy();}
}
