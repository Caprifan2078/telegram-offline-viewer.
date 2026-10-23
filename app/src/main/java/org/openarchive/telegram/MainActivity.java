package org.openarchive.telegram;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.os.Build;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.text.TextUtils;
import android.view.animation.DecelerateInterpolator;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
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
import android.widget.PopupMenu;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;
import android.widget.MediaController;
import android.webkit.MimeTypeMap;
import java.io.InputStream;
import android.content.SharedPreferences;
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
    private static final String[] MEDIA_LABELS={"Всё","Видеосообщения","Голосовые","Фото","Видео","Музыка","Файлы","GIF","Стикеры"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService mediaWorker=Executors.newFixedThreadPool(2);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> thumbs=new LruCache<String,Bitmap>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap b){return b.getByteCount();}
    };
    private final LruCache<String,String> durations=new LruCache<>(250);
    private MessageDb db;private DocumentFolder folder;private SavedStore savedStore;private boolean loadingMore=false;private int autoLoaded=PAGE;private int searchLoaded=PAGE;private boolean home=true;private boolean autoFloating=false;private LinearLayout bottomPager;
    private JSONArray archives=new JSONArray();private String activeKey="default",activeName="Архив";private long pinnedFocus=-1;
    private LinearLayout pinnedBar;
    private FrameLayout root, floatingPanel;
    private VerticalTimeline verticalTimeline;
    private final int[] participantColors={0xff315c79,0xff665184,0xff326c65,0xff75513f,0xff5d607f,0xff724d68,0xff52683a,0xff426884};
    private LinearLayout base,list,filterBar,bottomNav,searchArea,toolArea;
    private ScrollView scroll;private TextView title,subtitle,status,pageLabel;private Button navBack;private Button addChatButton;private String lastRenderedDay="";
    private EditText searchInput,authorInput;private CheckBox mediaOnly;
    private Button backPage,nextPage,quickButton,deepButton,folderButton,backButton;
    private String screen="chat",mediaKind="all",q="",byAuthor="",mine="",returnScreen="";
    private boolean deep=false,onlyMedia=false,importing=false;
    private int chatOffset=0,searchOffset=0,mediaOffset=0,favOffset=0,total=0,loadToken=0;
    private long highlightId=-1;private long lastVisibleMessage=-1;private int lastVisibleTop=0;private final Map<Long,View> renderedMessageViews=new HashMap<>();private long pendingRestoreId=-1;private int pendingRestoreTop=0;private boolean restoringBackup=false;
    private String info="";
    private MediaPlayer player;private String playingPath="";private TextureView activeTexture;
    private Runnable progressUpdater;private MessageDb.Attachment playingAttachment;private LinearLayout playerStrip;private TextView playerText;private Button playerPause;private FrameLayout inlineHolder;private int floatingStart=0;
    private RoundControls activeRound;
    private static final class RoundControls {
        SeekBar seek;
        TextView timer;
        ImageView preview;
        TextView overlay;
        boolean dragging=false,wasPlaying=false;
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        loadArchiveRegistry();
        savedStore=new SavedStore(this);
        db=new MessageDb(this,activeKey);
        mine=getPreferences(MODE_PRIVATE).getString("mine","");
        String old=activeTree();
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

    private void renderHome(){
        ++loadToken;list.removeAllViews();pinnedBar.removeAllViews();filterBar.removeAllViews();searchArea.removeAllViews();verticalTimeline.setVisibility(View.GONE);bottomPager.setVisibility(View.GONE);bottomNav.setVisibility(View.GONE);
        navBack.setVisibility(View.GONE);title.setText("Мои переписки");subtitle.setText("Сохранённые локальные архивы");status.setText("Все данные остаются на устройстве");
        TextView saved=text("★ Избранное и заметки",18,FG);saved.setPadding(px(14),px(18),px(8),px(18));saved.setBackground(shape(0xff304a64,17));
        list.addView(saved,p(-1,-2));saved.setOnClickListener(v->{home=false;screen="saved";renderSaved();});
        for(int i=0;i<archives.length();i++){
            JSONObject a=archives.optJSONObject(i);if(a==null)continue;
            String key=a.optString("key"),name=a.optString("name","Переписка");
            LinearLayout item=row();item.setPadding(px(14),px(12),px(14),px(12));item.setBackground(shape(PANEL,17));
            LinearLayout.LayoutParams margins=p(-1,72);margins.setMargins(0,px(6),0,px(6));list.addView(item,margins);
            TextView avatar=text(name.isEmpty()?"?":name.substring(0,1).toUpperCase(Locale.ROOT),21,FG);
            avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(participantColors[i%participantColors.length],100));item.addView(avatar,p(48,48));
            TextView label=text(name,17,FG);label.setPadding(px(14),0,0,0);item.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            item.setOnClickListener(v->activateArchive(key));
        }
        TextView add=text("＋ Добавить переписку",16,ACCENT);add.setPadding(px(14),px(20),px(14),px(20));list.addView(add,p(-1,-2));add.setOnClickListener(v->chooseFolder());
        scroll.post(()->scroll.scrollTo(0,0));
    }
    private void renderSaved(){
        ++loadToken;home=false;screen="saved";list.removeAllViews();filterBar.removeAllViews();searchArea.removeAllViews();pinnedBar.removeAllViews();bottomNav.setVisibility(View.GONE);bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        navBack.setVisibility(View.VISIBLE);title.setText("★ Избранное");subtitle.setText("Локальные заметки и сохранённые сообщения");
        LinearLayout compose=row();list.addView(compose,p(-1,-2));
        EditText input=new EditText(this);input.setHint("Написать заметку…");input.setTextColor(FG);input.setHintTextColor(SUB);compose.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        Button send=btn("➤");compose.addView(send,p(50,52));send.setOnClickListener(v->{String value=input.getText().toString().trim();if(value.isEmpty())return;worker.execute(()->{savedStore.add(value,null,null,null);runOnUiThread(this::renderSaved);});});
        Button attach=btn("📎 Прикрепить файл");list.addView(attach,p(-1,47));attach.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,28);});
        worker.execute(()->{List<SavedStore.Note> notes=savedStore.recent(150);runOnUiThread(()->{if(!screen.equals("saved"))return;
            for(SavedStore.Note n:notes){
                TextView text=text("★ "+(n.body==null?"":n.body)+(n.fileName!=null?"\n📎 "+n.fileName:""),15,FG);
                text.setPadding(px(14),px(12),px(14),px(12));text.setBackground(shape(PANEL,12));
                LinearLayout.LayoutParams mp=p(-1,-2);mp.setMargins(0,px(5),0,px(5));list.addView(text,mp);
                text.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Заметка").setItems(new String[]{"Удалить заметку"},(d,w)->worker.execute(()->{savedStore.delete(n.id);runOnUiThread(this::renderSaved);})).show();return true;});
                if(n.fileId!=null)text.setOnClickListener(v->{try{Intent action=new Intent(Intent.ACTION_VIEW);action.setDataAndType(Uri.parse(n.fileId),n.mime==null?"*/*":n.mime);action.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(action);}catch(Exception ignored){toast("Не удалось открыть файл");}});
            }
        });});
        // A global list of starred items across all registered archives.
        for(int i=0;i<archives.length();i++){
            JSONObject item=archives.optJSONObject(i);if(item==null)continue;
            String key=item.optString("key"),label=item.optString("name","Чат");
            worker.execute(()->{
                MessageDb other=new MessageDb(this,key);
                List<MessageDb.Item> favorites;
                try{favorites=other.page(0,50,"",false,"",false,true);}finally{other.close();}
                runOnUiThread(()->{if(!screen.equals("saved"))return;
                    for(MessageDb.Item m:favorites){
                        TextView t=text("★ "+label+" · "+day(m.date)+"\n"+clip(m.body,180),14,FG);
                        t.setPadding(px(12),px(12),px(12),px(12));t.setBackground(shape(CHIP,12));
                        LinearLayout.LayoutParams pad=p(-1,-2);pad.topMargin=px(5);list.addView(t,pad);
                        t.setOnClickListener(v->{activateArchive(key);jump(m.id,"");});
                    }
                });
            });
        }
    }
    private void appendMessages(){
        if(loadingMore||home||(!screen.equals("chat")&&!screen.equals("search")))return;
        boolean searching=screen.equals("search");
        int loaded=searching?searchLoaded:autoLoaded;
        if(loaded>=total)return;
        loadingMore=true;
        final int token=loadToken,offset=getOffset()+loaded;
        final String term=q,who=byAuthor;final boolean mode=deep,withMedia=onlyMedia;
        worker.execute(()->{
            try{
                List<MessageDb.Item> part=db.page(offset,PAGE,searching?term:"",searching&&mode,searching&&mode?who:"",searching&&mode&&withMedia,false);
                Map<Long,List<MessageDb.Attachment>> files=db.attachmentsForMessages(part);
                runOnUiThread(()->{
                    loadingMore=false;if(token!=loadToken||home||part.isEmpty())return;
                    if(searching)searchLoaded+=part.size();else autoLoaded+=part.size();
                    int y=scroll.getScrollY();
                    renderMessages(part,files,searching,true);
                    scroll.post(()->scroll.scrollTo(0,y));
                    pageLabel.setText((getOffset()+1)+"–"+Math.min(total,getOffset()+(searching?searchLoaded:autoLoaded))+" / "+total);
                });
            }catch(Exception error){runOnUiThread(()->{loadingMore=false;status.setText("Ошибка подгрузки: "+error.getMessage());});}
        });
    }
    private void saveReadingProgress(){
        if(lastVisibleMessage>0&&activeKey!=null){getPreferences(MODE_PRIVATE).edit().putLong("read_id_"+activeKey,lastVisibleMessage).putInt("read_top_"+activeKey,lastVisibleTop).apply();}
        if(home||!screen.equals("chat")||scroll==null||list==null)return;
        int child=0;int y=scroll.getScrollY();
        for(Map.Entry<Long,View> entry:renderedMessageViews.entrySet()){
            View v=entry.getValue();if(v.getTop()<=y+px(115)&&v.getBottom()>y+px(115)){
                lastVisibleMessage=entry.getKey();lastVisibleTop=v.getTop()-y;break;
            }
        }
        for(int i=0;i<list.getChildCount();i++){
            View view=list.getChildAt(i);
            if(view.getBottom()>y){child=i;break;}
        }
        int approx=chatOffset+Math.min(autoLoaded-1,Math.max(0,child/2));
        getPreferences(MODE_PRIVATE).edit().putInt("read_offset_"+activeKey,approx).apply();
    }
    private void checkAutoFloat(){
        if(player==null||inlineHolder==null||floatingPanel!=null||!screen.equals("chat"))return;
        try{if(!player.isPlaying())return;}catch(Exception badState){return;}
        int[] p=new int[2],viewport=new int[2];inlineHolder.getLocationOnScreen(p);scroll.getLocationOnScreen(viewport);
        if(p[1]+inlineHolder.getHeight()<viewport[1]||p[1]>viewport[1]+scroll.getHeight()){
            if(playingAttachment!=null&&playingAttachment.kind.equals("round")){
                int ms=0;try{ms=player.getCurrentPosition();}catch(Exception ignored){}
                floatingStart=ms;showFloatingRound(playingAttachment);autoFloating=true;
            }
        }
    }
    private void loadArchiveRegistry(){
        try{archives=new JSONArray(getPreferences(MODE_PRIVATE).getString("archive_registry","[]"));}catch(Exception e){archives=new JSONArray();}
        activeKey=getPreferences(MODE_PRIVATE).getString("active_archive","default");
        if(archives.length()==0){
            String old=getPreferences(MODE_PRIVATE).getString("tree",null);
            if(old!=null)try{JSONObject o=new JSONObject();o.put("key","default");o.put("name","Первая переписка");o.put("tree",old);archives.put(o);saveRegistry();}catch(Exception ignored){}
        }
        for(int i=0;i<archives.length();i++){JSONObject o=archives.optJSONObject(i);if(o!=null&&activeKey.equals(o.optString("key"))){activeName=o.optString("name","Архив");return;}}
        if(archives.length()>0){JSONObject o=archives.optJSONObject(0);activeKey=o.optString("key","default");activeName=o.optString("name","Архив");}
    }
    private void saveRegistry(){getPreferences(MODE_PRIVATE).edit().putString("archive_registry",archives.toString()).putString("active_archive",activeKey).apply();}
    private String activeTree(){for(int i=0;i<archives.length();i++){JSONObject o=archives.optJSONObject(i);if(o!=null&&activeKey.equals(o.optString("key")))return o.optString("tree",null);}return null;}
    private void registerArchive(String name,String tree){
        try{JSONObject o=new JSONObject();String key=UUID.randomUUID().toString().replace("-","");o.put("key",key);o.put("name",name);o.put("tree",tree);archives.put(o);activateArchive(key);}catch(Exception e){toast("Ошибка добавления чата");}
    }
    private void activateArchive(String key){
        saveReadingProgress();
        if(importing){toast("Импорт ещё идёт");return;}
        JSONObject choice=null;for(int i=0;i<archives.length();i++){JSONObject o=archives.optJSONObject(i);if(o!=null&&key.equals(o.optString("key")))choice=o;}
        if(choice==null)return;closeFloatingRound();stopPlayback();++loadToken;activeKey=key;activeName=choice.optString("name","Архив");saveRegistry();
        if(db!=null)db.close();db=new MessageDb(this,activeKey);
        String path=choice.optString("tree","");folder=path.isEmpty()?null:path.startsWith("local:")?new DocumentFolder(getContentResolver(),new java.io.File(path.substring(6))):new DocumentFolder(getContentResolver(),Uri.parse(path));
        chatOffset=searchOffset=mediaOffset=favOffset=0;autoLoaded=PAGE;home=false;screen="chat";returnScreen="";chatOffset=getPreferences(MODE_PRIVATE).getInt("read_offset_"+activeKey,0);
        pendingRestoreId=getPreferences(MODE_PRIVATE).getLong("read_id_"+activeKey,-1);
        pendingRestoreTop=getPreferences(MODE_PRIVATE).getInt("read_top_"+activeKey,0);
        if(pendingRestoreId>0)worker.execute(()->{int pos=db.positionFor(pendingRestoreId);runOnUiThread(()->{chatOffset=Math.max(0,pos-3);showPage();});});
        else showPage();
    }
    private void chatPicker(){
        String[] labels=new String[archives.length()+1];for(int i=0;i<archives.length();i++){JSONObject o=archives.optJSONObject(i);labels[i]=o==null?"Чат":o.optString("name","Чат");}
        labels[archives.length()]="＋ Добавить чат";
        new AlertDialog.Builder(this).setTitle("Мои архивы").setItems(labels,(d,i)->{
            if(i==archives.length())chooseFolder();else{JSONObject o=archives.optJSONObject(i);if(o!=null)activateArchive(o.optString("key"));}
        }).show();
    }
    private void moreMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add("⇈ В начало");menu.getMenu().add("⇊ В конец");menu.getMenu().add("📌 Закреплённые");menu.getMenu().add("Мои архивы");menu.getMenu().add("Цвета участников");
        menu.setOnMenuItemClickListener(item->{String x=item.getTitle().toString();
            if(x.contains("начало")){chatOffset=0;showPage();}
            else if(x.contains("конец")){worker.execute(()->{int n=db.totalMessages();runOnUiThread(()->{chatOffset=Math.max(0,n-PAGE);showPage();});});}
            else if(x.contains("Закрепл"))showPinList();
            else if(x.contains("Цвета"))participantSettings();else chatPicker();return true;
        });menu.show();
    }
    /** Compact Telegram-like pinned preview: tap the message to jump, arrow to switch, cross to unpin. */
    private void renderPinnedBar(){
        pinnedBar.removeAllViews();
        if(!screen.equals("chat")||home)return;
        final String key=activeKey;
        worker.execute(()->{
            List<MessageDb.Item> pins=db.pinned();
            runOnUiThread(()->{
                if(!screen.equals("chat")||home||!key.equals(activeKey))return;
                pinnedBar.animate().cancel();pinnedBar.setAlpha(1f);pinnedBar.removeAllViews();if(pins.isEmpty()){pinnedFocus=-1;return;}
                int selected=0;
                for(int i=0;i<pins.size();i++)if(pins.get(i).id==pinnedFocus){selected=i;break;}
                MessageDb.Item current=pins.get(selected);pinnedFocus=current.id;
                LinearLayout bar=row();bar.setPadding(px(8),px(5),px(6),px(5));
                bar.setBackground(shape(0xff1b3548,12));
                LinearLayout.LayoutParams outer=p(-1,62);outer.setMargins(px(2),px(3),px(2),px(5));pinnedBar.addView(bar,outer);
                TextView stripe=text("┃",28,ACCENT);stripe.setGravity(Gravity.CENTER);
                bar.addView(stripe,p(16,49));
                LinearLayout info=column();info.setGravity(Gravity.CENTER_VERTICAL);
                info.setPadding(px(4),0,px(4),0);
                bar.addView(info,new LinearLayout.LayoutParams(0,-1,1));
                TextView caption=text("📌 Закреплённое сообщение  ·  "+(selected+1)+"/"+pins.size(),11,ACCENT);
                caption.setTypeface(null,Typeface.BOLD);info.addView(caption);
                String summary=current.body==null||current.body.trim().isEmpty()?"Медиафайл":current.body;
                TextView snippet=text(clip(summary,78),13,FG);snippet.setSingleLine(true);info.addView(snippet);
                info.setOnClickListener(v->jump(current.id,""));
                final int nextIndex=(selected+1)%pins.size();
                TextView next=text("⌄",27,FG);next.setGravity(Gravity.CENTER);
                bar.addView(next,p(34,45));next.setOnClickListener(v->{
                    pinnedFocus=pins.get(nextIndex).id;renderPinnedBar();
                });
                TextView unpin=text("✕",20,SUB);unpin.setGravity(Gravity.CENTER);bar.addView(unpin,p(32,45));
                unpin.setOnClickListener(v->unpinMessage(current.id));
                bar.setOnLongClickListener(v->{showPinList();return true;});
                bar.setAlpha(0f);bar.setTranslationY(-px(8));bar.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
            });
        });
    }
    private void unpinMessage(long id){
        new AlertDialog.Builder(this).setTitle("Открепить сообщение?").setMessage("Сообщение останется в переписке, но исчезнет из закреплённых.").setNegativeButton("Отмена",null).setPositiveButton("Открепить",(d,w)->unpinConfirmed(id)).show();
    }
    private void unpinConfirmed(long id){
        worker.execute(()->{
            if(db.isPinned(id))db.togglePinned(id);
            runOnUiThread(()->{if(pinnedFocus==id)pinnedFocus=-1;toast("Сообщение откреплено");renderPinnedBar();});
        });
    }
    private void pinnedItemMenu(MessageDb.Item message){
        new AlertDialog.Builder(this).setTitle("📌 Закреплённое сообщение")
            .setItems(new String[]{"↗ Перейти к сообщению","Открепить сообщение"},(d,choice)->{
                if(choice==0){pinnedFocus=message.id;jump(message.id,"");}
                else unpinMessage(message.id);
            }).show();
    }
    private void showPinList(){
        worker.execute(()->{
            List<MessageDb.Item> pins=db.pinned();
            runOnUiThread(()->{
                if(pins.isEmpty()){toast("Нет закреплённых сообщений");return;}
                String[] labels=new String[pins.size()];
                for(int i=0;i<pins.size();i++)
                    labels[i]=(i+1)+". "+day(pins.get(i).date)+" · "+clip(pins.get(i).body==null||pins.get(i).body.isEmpty()?"Медиа":pins.get(i).body,65);
                new AlertDialog.Builder(this).setTitle("Закреплённые сообщения")
                    .setItems(labels,(d,i)->{pinnedFocus=pins.get(i).id;jump(pinnedFocus,"");})
                    .setNeutralButton("Открепить…",(d,i)->{
                        new AlertDialog.Builder(this).setTitle("Что открепить?")
                            .setItems(labels,(dialog,at)->unpinMessage(pins.get(at).id)).show();
                    }).show();
            });
        });
    }
    private void createUi(){
        root=new FrameLayout(this);
        base=column();base.setBackgroundColor(BG);base.setPadding(px(9),px(5),px(9),px(6));
        root.addView(base,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        // Android 15+ uses edge-to-edge: protect the entire header from status bar and cutout.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                Insets safe=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
                base.setPadding(px(9),safe.top+px(6),px(9),safe.bottom+px(6));
            }else{
                base.setPadding(px(9),insets.getSystemWindowInsetTop()+px(6),px(9),insets.getSystemWindowInsetBottom()+px(6));
            }
            return insets;
        });root.requestApplyInsets();
        LinearLayout head=row();base.addView(head,p(-1,55));
        navBack=btn("‹");navBack.setTextSize(25);navBack.setGravity(Gravity.CENTER);
        head.addView(navBack,p(40,42));
        navBack.setOnClickListener(v->{if(!home){saveReadingProgress();home=true;screen="chat";returnScreen="";showPage();}});
        TextView logo=text("◉",26,ACCENT);logo.setGravity(Gravity.CENTER);head.addView(logo,p(37,42));
        LinearLayout headings=column();headings.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(headings,new LinearLayout.LayoutParams(0,px(50),1));
        title=text("Telegram Archive",17,FG);title.setTypeface(null,Typeface.BOLD);
        title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);headings.addView(title,p(-1,26));
        subtitle=text("Личный архив · офлайн",11,SUB);
        subtitle.setSingleLine(true);subtitle.setEllipsize(TextUtils.TruncateAt.END);headings.addView(subtitle,p(-1,19));
        Button settings=btn("⚙");head.addView(settings,p(38,42));settings.setOnClickListener(v->settingsMenu());
        folderButton=btn("＋");folderButton.setTextSize(22);head.addView(folderButton,p(41,42));
        folderButton.setOnClickListener(v->chooseFolder());
        playerStrip=row();playerStrip.setVisibility(View.GONE);playerStrip.setBackground(shape(0xff244159,13));base.addView(playerStrip,p(-1,45));
        playerText=text("Медиа",12,FG);playerStrip.addView(playerText,new LinearLayout.LayoutParams(0,-2,1));
        Button prev=btn("⏮");playerStrip.addView(prev,p(38,40));prev.setOnClickListener(v->playAdjacent(false));
        playerPause=btn("⏸");playerStrip.addView(playerPause,p(38,40));playerPause.setOnClickListener(v->toggleActivePlayback());
        Button next=btn("⏭");playerStrip.addView(next,p(38,40));next.setOnClickListener(v->playAdjacent(true));
        Button close=btn("✕");playerStrip.addView(close,p(38,40));close.setOnClickListener(v->{closeFloatingRound();stopPlayback();});
        bottomNav=row();base.addView(bottomNav,p(-1,51));
        addTab("💬 Чат","chat");addTab("▦ Медиа","media");addTab("★ Избранное","favorites");addTab("⌕ Поиск","search");
        toolArea=column();base.addView(toolArea,p(-1,-2));
        pinnedBar=row();base.addView(pinnedBar,p(-1,-2));
        filterBar=column();toolArea.addView(filterBar,p(-1,-2));
        searchArea=column();toolArea.addView(searchArea,p(-1,-2));
        status=text("",11,SUB);status.setPadding(px(8),px(4),px(8),px(6));status.setMaxLines(3);base.addView(status,p(-1,-2));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,ox,oy)->{if(!home&&(screen.equals("chat")||screen.equals("search"))&&!loadingMore&&list.getHeight()>0&&y+scroll.getHeight()>list.getHeight()-px(300)&&((screen.equals("chat")?autoLoaded:searchLoaded)<total)){appendMessages();}checkAutoFloat();saveReadingProgress();});
        FrameLayout scrollHost=new FrameLayout(this);
        base.addView(scrollHost,new LinearLayout.LayoutParams(-1,0,1));
        scrollHost.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        verticalTimeline=new VerticalTimeline();
        scrollHost.addView(verticalTimeline,new FrameLayout.LayoutParams(px(25),-1,Gravity.END));
        list=column();list.setPadding(px(3),px(6),px(3),px(14));scroll.addView(list);
        LinearLayout bottom=row();bottomPager=bottom;base.addView(bottom,p(-1,52));
        backPage=btn("‹ Назад");nextPage=btn("Далее ›");pageLabel=text("",12,SUB);pageLabel.setGravity(Gravity.CENTER);
        bottom.addView(backPage,p(95,44));bottom.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));bottom.addView(nextPage,p(95,44));
        backPage.setOnClickListener(v->{setOffset(Math.max(0,getOffset()-getLimit()));showPage();});
        nextPage.setOnClickListener(v->{setOffset(getOffset()+getLimit());showPage();});
        configureBars();
    }
    private void addTab(String label,String key){
        TextView t=text(label,12,SUB);t.setGravity(Gravity.CENTER);t.setTag(key);
        bottomNav.addView(t,new LinearLayout.LayoutParams(0,px(45),1));
        t.setOnClickListener(v->{if(floatingPanel==null)stopPlayback();screen=key;returnScreen="";highlightId=-1;showPage();});
    }
    private void refreshTabs(){
        for(int i=0;i<bottomNav.getChildCount();i++){
            TextView t=(TextView)bottomNav.getChildAt(i);boolean selected=screen.equals(t.getTag());
            t.setBackground(shape(selected?CHIP:PANEL,13));t.setTextColor(selected?FG:SUB);
        }
        navBack.setVisibility(View.VISIBLE);title.setText(activeName);
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
            Button more=btn("⋮");actions.addView(more,p(40,42));more.setOnClickListener(v->moreMenu(more));
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
        searchOffset=0;searchLoaded=PAGE;configureBars();hideKeyboard();showPage();
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
        new DatePickerDialog(this,(view,y,m,d)->{
            final String requested=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);
            status.setText("Ищу переписку по дате "+requested+"…");
            worker.execute(()->{
                MessageDb.DateMatch found=db.nearestDate(y,m+1,d);
                runOnUiThread(()->{
                    if(found.position<0){toast("В переписке нет сообщений с распознанной датой");return;}
                    screen="chat";chatOffset=Math.max(0,found.position-5);highlightId=-1;returnScreen="";
                    showPage();
                    if(!requested.equals(found.day))toast("Ближайшая дата переписки: "+found.day);
                });
            });
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void chooseFolder(){
        new AlertDialog.Builder(this).setTitle("Добавить переписку").setItems(
            new String[]{"Выбрать папку Telegram","Выбрать ZIP или RAR"},
            (d,selected)->{if(selected==0)chooseExistingFolder();else chooseCompressedArchive();}).show();
    }
    private void chooseCompressedArchive(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,57);
    }
    private void chooseExistingFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_FOLDER);
    }
    @Override public void onBackPressed(){if(screen.equals("saved")||(!home&&returnScreen.isEmpty())){saveReadingProgress();home=true;screen="chat";bottomNav.setVisibility(View.VISIBLE);showPage();return;}if(!returnScreen.isEmpty()){restoreReturn();return;}super.onBackPressed();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if((request==41||request==42)&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            backupAction(data.getData(),request==42);return;
        }
        if(request==28&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            String label=uri.getLastPathSegment();String mime=getContentResolver().getType(uri);
            worker.execute(()->{savedStore.add("",label,uri.toString(),mime);runOnUiThread(this::renderSaved);});return;
        }
        if(request==57&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri source=data.getData();String name="Telegram.zip";
            try(android.database.Cursor c=getContentResolver().query(source,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(c!=null&&c.moveToFirst())name=c.getString(0);
            }catch(Exception ignored){}
            String low=name==null?"":name.toLowerCase(Locale.ROOT);
            String ext=low.endsWith(".rar")?"rar":low.endsWith(".zip")?"zip":"";
            if(ext.isEmpty()){toast("Нужен ZIP или RAR");return;}
            final String displayName=name;
            new AlertDialog.Builder(this).setTitle("Импорт "+displayName)
                .setMessage("Архив автоматически распакуется во внутреннюю папку приложения. Для медиа потребуется свободное место.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Импортировать",(d,w)->importCompressed(source,ext,displayName)).show();
            return;
        }
        if(request!=PICK_FOLDER||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(Exception e){status.setText("Нет доступа к папке: "+e.getMessage());return;}
        EditText chatName=new EditText(this);chatName.setSingleLine(true);chatName.setHint("Например, переписка с Ваней");
        new AlertDialog.Builder(this).setTitle("Название чата").setView(chatName).setPositiveButton("Добавить",(dd,ww)->{
        String name=chatName.getText().toString().trim();if(name.isEmpty())name="Чат "+(archives.length()+1);
        registerArchive(name,uri.toString());
        new AlertDialog.Builder(this).setTitle("Импортировать экспорт Telegram?")
                .setMessage("Будут проиндексированы все messages*.html. Фото, видео и голосовые останутся в этой папке. Текущие сообщения заменятся только после успешного импорта. Избранное по возможности сохранится.")
                .setPositiveButton("Импортировать",(d,w)->importArchive()).setNegativeButton("Отмена",null).show();
        }).setNegativeButton("Отмена",null).show();
    }
    private void importCompressed(Uri source,String extension,String label){
        if(importing)return;
        importing=true;folderButton.setEnabled(false);status.setText("Распаковка "+label+"…");
        String key=UUID.randomUUID().toString().replace("-","");
        worker.execute(()->{
            try{
                java.io.File root=ArchiveFileImport.extract(this,source,extension,key,msg->runOnUiThread(()->status.setText(msg)));
                MessageDb fresh=new MessageDb(this,key);
                try{TelegramImporter.importFiles(getContentResolver(),new DocumentFolder(getContentResolver(),root),fresh,msg->runOnUiThread(()->status.setText(msg)));}finally{fresh.close();}
                runOnUiThread(()->{
                    importing=false;folderButton.setEnabled(true);
                    try{
                        JSONObject o=new JSONObject();o.put("key",key);o.put("name",label);
                        o.put("tree","local:"+root.getAbsolutePath());archives.put(o);
                        activateArchive(key);status.setText("Импорт завершён");
                    }catch(Exception error){toast("Ошибка регистрации чата: "+error.getMessage());}
                });
            }catch(Exception error){
                ArchiveFileImport.deleteTree(new java.io.File(new java.io.File(getFilesDir(),"telegram_imports"),key));
                runOnUiThread(()->{importing=false;folderButton.setEnabled(true);status.setText("Ошибка архива: "+error.getMessage());});
            }
        });
    }
    private void importArchive(){
        if(folder==null||importing)return;closeFloatingRound();stopPlayback();importing=true;folderButton.setEnabled(false);status.setText("Создаю локальную базу, не копируя медиа…");
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
        if(screen.equals("saved")){renderSaved();return;}
        if(!home)bottomNav.setVisibility(View.VISIBLE);
        if(importing)return;if(floatingPanel==null)stopPlayback();refreshTabs();configureBars();renderPinnedBar();int token=++loadToken;
        if(home){renderHome();return;}
        final String tab=screen,kind=mediaKind,search=q,who=byAuthor;
        final int requested=getOffset();final boolean deepMode=deep,hasMedia=onlyMedia;
        backPage.setEnabled(false);nextPage.setEnabled(false);status.setText("Загружаю…");
        worker.execute(()->{
            try{
                boolean gallery="media".equals(tab),find="search".equals(tab),favs="favorites".equals(tab);
                int count=gallery?db.mediaCount(kind):db.count(find?search:"",find&&deepMode,find&&deepMode?who:"",find&&deepMode&&hasMedia,favs);
                int pos=count==0?0:Math.min(requested,Math.max(0,count-1));
                List<MessageDb.Attachment> attachments=gallery?db.mediaPage(kind,pos,MEDIA_PAGE):null;
                List<MessageDb.Item> messages=gallery?null:db.page(pos,tab.equals("chat")?Math.max(PAGE,autoLoaded):tab.equals("search")?Math.max(PAGE,searchLoaded):PAGE,find?search:"",find&&deepMode,find&&deepMode?who:"",find&&deepMode&&hasMedia,favs);
                Map<Long,List<MessageDb.Attachment>> links=messages==null?new HashMap<>():db.attachmentsForMessages(messages);
                MessageDb.SearchStats stats=find?db.searchStats(search,deepMode,deepMode?who:"",deepMode&&hasMedia):null;
                runOnUiThread(()->{
                    if(isFinishing()||token!=loadToken)return;
                    total=count;setOffset(pos);list.removeAllViews();
                    if(gallery)renderMedia(attachments);else renderMessages(messages,links,find);
                    bottomPager.setVisibility(tab.equals("chat")||tab.equals("search")?View.GONE:View.VISIBLE);
                    int shown=gallery?attachments.size():messages.size();
                    verticalTimeline.setVisibility("chat".equals(tab)?View.VISIBLE:View.GONE);
                    if("chat".equals(tab))verticalTimeline.setPosition(pos,count);
                    pageLabel.setText(count==0?"Нет данных":(pos+1)+"–"+(pos+shown)+" / "+count);
                    backPage.setEnabled(pos>0);nextPage.setEnabled(pos+shown<count);
                    if(count==0)status.setText(gallery?"Таких медиа пока нет. Попробуй повторный импорт исходных HTML.":
                            "Ничего не найдено — попробуй другой запрос или выбери папку.");
                    else status.setText("media".equals(tab)?"Нажми — воспроизвести · удерживай — действия":
                            "search".equals(tab)?(search.isEmpty()?"Введите слово или фразу":("Сообщений: "+count+"  ·  Точных вхождений «"+clip(search,28)+"»: "+stats.literalOccurrences+(deepMode?"":"  ·  Быстрый поиск учитывает начала слов"))):
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
        renderMessages(messages,files,results,false);
    }
    private void renderMessages(List<MessageDb.Item> messages,Map<Long,List<MessageDb.Attachment>> files,boolean results,boolean append){
        if(messages.isEmpty())return;
        if(!append)renderedMessageViews.clear();
        String last=append?lastRenderedDay:"";View focus=null;View glowBubble=null;int glowBase=0;long glowId=-1;
        for(MessageDb.Item m:messages){
            String date=day(m.date);if(!date.equals(last)){dateSeparator(date);last=date;}
            boolean own=!mine.isEmpty()&&mine.equalsIgnoreCase(m.author==null?"":m.author.trim());
            LinearLayout line=row();line.setGravity(own?Gravity.RIGHT:Gravity.LEFT);
            LinearLayout.LayoutParams linep=p(-1,-2);linep.setMargins(px(2),px(1),px(2),px(4));list.addView(line,linep);
            if(!own){TextView avatar=text(initial(m.author),14,FG);avatar.setTypeface(null,Typeface.BOLD);
                avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(authorColor(m.author),100));
                LinearLayout.LayoutParams ap=p(32,32);ap.rightMargin=px(7);line.addView(avatar,ap);}
            LinearLayout bubble=column();bubble.setPadding(px(9),px(6),px(9),px(7));
            int chosenColor=authorColor(m.author);
            bubble.setBackground(gradient(m.id==highlightId?0xff477c91:chosenColor,
                    m.id==highlightId?0xff33576b:darken(chosenColor),17));
            if(m.id==highlightId){glowBubble=bubble;glowBase=chosenColor;glowId=m.id;line.setBackground(shape(0x443ad0fc,12));}
            LinearLayout.LayoutParams bp=p(-2,-2);line.addView(bubble,bp);
            int maxWidth=getResources().getDisplayMetrics().widthPixels-px(93);
            if(maxWidth<px(190))maxWidth=px(240);final int mw=maxWidth;
            bubble.setOnLongClickListener(v->{messageActions(m);return true;});
            String author=m.author==null||m.author.isEmpty()?"Неизвестный отправитель":m.author;
            TextView from=text(author,12,0xffe8f6ff);from.setMaxWidth(mw);from.setTypeface(null,Typeface.BOLD);bubble.addView(from);
            from.setOnLongClickListener(v->{pickAuthorColor(author);return true;});
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
            renderedMessageViews.put(m.id,line);
            if(m.id==highlightId)focus=line;
        }
        lastRenderedDay=last;
        if(append)return; // Never jump to the top when more history is appended.
        if(pendingRestoreId>0){
            final View restored=renderedMessageViews.get(pendingRestoreId);
            final int top=pendingRestoreTop;
            pendingRestoreId=-1;
            if(restored!=null){scroll.post(()->scroll.scrollTo(0,Math.max(0,restored.getTop()-top)));return;}
        }
        final View target=focus;
        final View targetBubble=glowBubble;
        final int color=glowBase;
        final long id=glowId;
        scroll.post(()->{
            if(target!=null){
                scroll.smoothScrollTo(0,Math.max(0,target.getTop()-px(120)));
                if(targetBubble!=null){
                    targetBubble.postDelayed(()->pulseMessage(targetBubble,color,id),190);
                }
            }else scroll.scrollTo(0,0);
        });
    }
    private int mixColors(int a,int b,float t){
        return Color.rgb(
            Math.round(Color.red(a)*(1-t)+Color.red(b)*t),
            Math.round(Color.green(a)*(1-t)+Color.green(b)*t),
            Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    private void pulseMessage(View bubble,int background,long id){
        if(highlightId!=id||!screen.equals("chat")||home)return;
        android.graphics.drawable.Drawable drawable=bubble.getBackground();
        if(!(drawable instanceof GradientDrawable))return;
        GradientDrawable bg=(GradientDrawable)drawable;
        ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);
        animator.setDuration(1750);animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation->{
            if(bubble.getParent()==null)return;
            float t=(float)animation.getAnimatedValue();
            bg.setColors(new int[]{mixColors(0xff78cae4,background,t),mixColors(0xff397c9d,darken(background),t)});
        });
        animator.start();
        handler.postDelayed(()->{if(highlightId==id)highlightId=-1;},1850);
    }
    private String initial(String s){if(s==null||s.isEmpty())return "?";return s.substring(0,1).toUpperCase(Locale.ROOT);}
    private void messageActions(MessageDb.Item m){
        String[] options={m.favorite?"Убрать из избранного":"★ Добавить в избранное",db.isPinned(m.id)?"Открепить":"📌 Закрепить сообщение","Скопировать текст","Перейти к сообщению"};
        new AlertDialog.Builder(this).setTitle("Сообщение · "+clock(m.date)).setItems(options,(dia,which)->{
            if(which==0)toggleStar(m.id);
            else if(which==1){worker.execute(()->{boolean pin=db.togglePinned(m.id);runOnUiThread(()->{toast(pin?"Закреплено":"Откреплено");renderPinnedBar();});});}
            else if(which==2){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
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
    private String mediaLabel(String k){switch(k){case "round":return "Видеосообщение";case "voice":return "Голосовое";case "video":return "Видео";case "audio":return "Аудио / музыка";case "image":return "Фото";case "gif":return "GIF";case "sticker":return "Стикер";default:return "Файл";}}
    private View mediaTile(MessageDb.Attachment a,boolean gallery){
        if(a.kind.equals("round"))return circle(a,gallery?124:190,!gallery);
        if(a.kind.equals("voice")||a.kind.equals("audio"))return voice(a,gallery);
        if(a.kind.equals("image")||a.kind.equals("gif")||a.kind.equals("sticker")||a.kind.equals("video")){
            FrameLayout box=new FrameLayout(this);int size=gallery?84:170;
            box.setBackground(shape(CHIP,11));box.setClipToOutline(true);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(image,new FrameLayout.LayoutParams(-1,-1));loadThumbnail(a.path,a.kind,image);
            if(a.kind.equals("video")){TextView play=text("▶",27,FG);play.setGravity(Gravity.CENTER);box.addView(play,new FrameLayout.LayoutParams(-1,-1));}
            box.setOnClickListener(v->openMedia(a));box.setOnLongClickListener(v->{mediaActions(a);return true;});
            FrameLayout outer=new FrameLayout(this);outer.addView(box,new FrameLayout.LayoutParams(px(size),px(gallery?84:128)));return outer;
        }
        TextView file=text("📎 "+clip(filename(a.path),gallery?18:33),13,ACCENT);file.setPadding(px(8),px(10),px(8),px(10));
        file.setBackground(shape(CHIP,12));file.setOnClickListener(v->openMedia(a));
        file.setOnLongClickListener(v->{mediaActions(a);return true;});return file;
    }
    private String filename(String path){int p=path.lastIndexOf('/');return p<0?path:path.substring(p+1);}
    /** Tap: expand and play in-chat; tap again: pause/resume. Drag the bar to seek. */
    private View circle(MessageDb.Attachment a,int size,boolean chatCircle){
        FrameLayout holder=new FrameLayout(this);holder.setBackground(shape(0xff31526c,100));
        holder.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}
        });holder.setClipToOutline(true);
        ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.addView(preview,new FrameLayout.LayoutParams(-1,-1));loadThumbnail(a.path,"round",preview);
        TextView overlay=text("▶",size>130?38:28,FG);overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(0x38000000);holder.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout wrap=column();wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.addView(holder,p(size,size));
        TextView duration=text("00:00",12,SUB);duration.setGravity(Gravity.CENTER);wrap.addView(duration,p(size,24));
        loadDuration(a.path,duration);
        RoundControls controls=new RoundControls();
        controls.timer=duration;controls.preview=preview;controls.overlay=overlay;
        if(chatCircle){
            SeekBar seek=new SeekBar(this);seek.setMax(1000);seek.setProgress(0);
            seek.setVisibility(View.GONE);wrap.addView(seek,p(size,34));controls.seek=seek;
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onStartTrackingTouch(SeekBar bar){
                    controls.dragging=true;controls.wasPlaying=false;
                    try{if(player!=null&&a.path.equals(playingPath)){
                        controls.wasPlaying=player.isPlaying();if(controls.wasPlaying)player.pause();
                    }}catch(Exception ignored){}
                }
                @Override public void onProgressChanged(SeekBar bar,int value,boolean user){
                    if(!user)return;
                    try{if(player!=null&&a.path.equals(playingPath)){
                        int length=Math.max(0,player.getDuration());
                        duration.setText(formatTime(Math.round(length*value/1000f)/1000)+" / "+formatTime(length/1000));
                    }}catch(Exception ignored){}
                }
                @Override public void onStopTrackingTouch(SeekBar bar){
                    try{if(player!=null&&a.path.equals(playingPath)){
                        int length=Math.max(0,player.getDuration());
                        player.seekTo(Math.round(length*bar.getProgress()/1000f));
                        if(controls.wasPlaying){player.start();overlay.setVisibility(View.GONE);}
                    }}catch(Exception e){toast("Не удалось перемотать видео");}
                    controls.dragging=false;
                }
            });
        }
        android.view.ScaleGestureDetector videoZoom=new android.view.ScaleGestureDetector(this,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
            float zoom=1f;
            @Override public boolean onScale(android.view.ScaleGestureDetector detector){
                zoom=Math.max(1f,Math.min(3f,zoom*detector.getScaleFactor()));
                holder.setScaleX(zoom);holder.setScaleY(zoom);return true;
            }
        });
        holder.setOnTouchListener((v,e)->{videoZoom.onTouchEvent(e);return e.getPointerCount()>1;});
        holder.setTag(controls);
        holder.setOnClickListener(v->{
            if(chatCircle){
                int max=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-105;
                int enlarged=Math.max(size,Math.min(260,max));
                if(holder.getLayoutParams().width<px(enlarged)){
                    holder.setLayoutParams(p(enlarged,enlarged));duration.setLayoutParams(p(enlarged,25));
                }
                if(controls.seek!=null){
                    controls.seek.setVisibility(View.VISIBLE);
                    controls.seek.setLayoutParams(p(enlarged,35));
                }
                wrap.requestLayout();
            }
            startRoundPlayback(a,holder,preview,overlay);
        });
        holder.setOnLongClickListener(v->{mediaActions(a);return true;});
        return wrap;
    }
    private View voice(MessageDb.Attachment a,boolean gallery){
        LinearLayout outer=column();outer.setBackground(shape(CHIP,14));outer.setPadding(px(8),px(5),px(8),px(5));
        LinearLayout inner=row();outer.addView(inner,p(gallery?98:218,47));
        TextView play=text("▶",23,FG);play.setGravity(Gravity.CENTER);inner.addView(play,p(32,42));
        LinearLayout details=column();details.setPadding(px(5),0,0,0);inner.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        TextView bars=text("▂▆▃▇▄▅▂▆▃▄▇▂▅",gallery?11:15,ACCENT);bars.setSingleLine(true);details.addView(bars);
        TextView duration=text("00:00",11,SUB);details.addView(duration);loadDuration(a.path,duration);
        play.setOnClickListener(v->{
            startAudio(a.path,play,duration);
            playingAttachment=a;
            playerStrip.setVisibility(View.VISIBLE);
            playerText.setText((a.kind.equals("voice")?"Голосовое · ":"Музыка · ")+(a.author==null?"":a.author));
        });
        outer.setOnLongClickListener(v->{mediaActions(a);return true;});return outer;
    }
    private void mediaActions(MessageDb.Attachment a){
        String[] names=a.kind.equals("round")?
            new String[]{"▶ Плавающее видео-сообщение","⛶ Полный экран / PiP","↗ Перейти к сообщению","☆ В избранное","Открыть внешним плеером"}:
            new String[]{"▶ Воспроизвести / открыть","↗ Перейти к сообщению","☆ Отметить сообщение","⛶ Открыть полностью"};
        new AlertDialog.Builder(this).setTitle(mediaLabel(a.kind)+" · "+day(a.date)).setItems(names,(dlg,which)->{
            if(a.kind.equals("round")){
                if(which==0)showFloatingRound(a);
                else if(which==1)openRoundPlayer(a);
                else if(which==2)jump(a.messageId,screen.equals("media")?"media":"");
                else if(which==3)toggleStar(a.messageId);
                else resolveMedia(a.path,uri->external(uri,a.path));
            }else{
                if(which==0||which==3)openMedia(a);
                if(which==1)jump(a.messageId,"media");
                if(which==2)toggleStar(a.messageId);
            }
        }).show();
    }
    private void jump(long id,String source){
        status.setText("Перехожу к сообщению…");
        worker.execute(()->{
            int at=db.hasMessage(id)?db.positionFor(id):-1;
            List<MessageDb.Item> candidates=db.pinned();
            long nextPinned=-1;
            for(MessageDb.Item candidate:candidates)if(candidate.id<id){nextPinned=candidate.id;break;}
            if(nextPinned<0&&!candidates.isEmpty())nextPinned=candidates.get(0).id;
            final long rotation=nextPinned;
            runOnUiThread(()->{
                if(at<0){toast("Сообщение не найдено");return;}
                if(rotation>=0)pinnedFocus=rotation;
                home=false;autoLoaded=PAGE;
                returnScreen=source;screen="chat";chatOffset=Math.max(0,at-7);highlightId=id;showPage();
            });
        });
    }
    private void restoreReturn(){
        if(returnScreen.isEmpty())return;
        screen=returnScreen;returnScreen="";highlightId=-1;showPage();
    }

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
        if(a.kind.equals("gif")&&a.path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|m4v)(\\?.*)?$")){resolveMedia(a.path,this::fullVideo);return;}
        if(a.kind.equals("image")||a.kind.equals("sticker")||a.kind.equals("gif")){openPhotoGallery(a);return;}
        if(a.kind.equals("round")){showFloatingRound(a);return;}
        if(a.kind.equals("video"))resolveMedia(a.path,this::fullVideo);
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
        if(playerStrip!=null)playerStrip.setVisibility(View.GONE);
        inlineHolder=null;playingAttachment=null;
        if(player!=null){
            try{player.stop();}catch(Exception ignored){}
            try{player.release();}catch(Exception ignored){}
            player=null;
        }
        if(activeTexture!=null){
            TextureView previous=activeTexture;
            activeTexture=null;
            ViewGroup parent=previous.getParent() instanceof ViewGroup?(ViewGroup)previous.getParent():null;
            if(parent!=null)parent.removeView(previous);
        }
        if(activeRound!=null){
            activeRound.preview.setVisibility(View.VISIBLE);
            activeRound.overlay.setVisibility(View.VISIBLE);
            if(activeRound.seek!=null)activeRound.seek.setProgress(0);
            activeRound=null;
        }
        playingPath="";
    }
    /** TextureView lets Android crop video to a true circle while the SeekBar remains outside it. */
    private void startInlineRound(String path,FrameLayout holder,ImageView poster,TextView overlay){
        if(player!=null&&path.equals(playingPath)){
            try{
                if(player.isPlaying()){player.pause();overlay.setVisibility(View.VISIBLE);}
                else{player.start();overlay.setVisibility(View.GONE);}
            }catch(Exception ignored){}
            return;
        }
        stopPlayback();
        inlineHolder=holder;
        RoundControls controls=holder.getTag() instanceof RoundControls?(RoundControls)holder.getTag():null;
        if(controls!=null)activeRound=controls;
        resolveMedia(path,uri->{
            playingPath=path;
            inlineHolder=holder;
            TextureView texture=new TextureView(this);activeTexture=texture;
            holder.addView(texture,1,new FrameLayout.LayoutParams(-1,-1));
            overlay.bringToFront();
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int w,int h){
                    if(!path.equals(playingPath))return;
                    try{
                        MediaPlayer p=new MediaPlayer();player=p;
                        Surface screenSurface=new Surface(surface);
                        p.setSurface(screenSurface);screenSurface.release();
                        p.setDataSource(MainActivity.this,uri);p.setLooping(true);
                        p.setOnPreparedListener(mp->{
                            if(mp!=player)return;
                            Matrix transform=new Matrix();
                            float vw=Math.max(1,mp.getVideoWidth()),vh=Math.max(1,mp.getVideoHeight());
                            float scale=Math.max(w/vw,h/vh);
                            transform.setScale(vw*scale/w,vh*scale/h,w/2f,h/2f);
                            texture.setTransform(transform);
                            overlay.setVisibility(View.GONE);poster.setVisibility(View.INVISIBLE);mp.start();
                            if(controls!=null){
                                controls.timer.setText("0:00 / "+formatTime(Math.max(0,mp.getDuration()/1000)));
                                progressUpdater=new Runnable(){
                                    @Override public void run(){
                                        if(player!=mp)return;
                                        try{
                                            int current=Math.max(0,mp.getCurrentPosition());
                                            int length=Math.max(1,mp.getDuration());
                                            if(!controls.dragging){
                                                if(controls.seek!=null)
                                                    controls.seek.setProgress(Math.min(1000,Math.round(1000f*current/length)));
                                                controls.timer.setText(formatTime(current/1000)+" / "+formatTime(length/1000));
                                            }
                                            handler.postDelayed(this,250);
                                        }catch(Exception ignored){}
                                    }
                                };
                                handler.post(progressUpdater);
                            }
                        });
                        p.setOnErrorListener((mp,what,extra)->{
                            toast("Не удалось воспроизвести видео-сообщение. Удержи для полного экрана.");
                            stopPlayback();overlay.setVisibility(View.VISIBLE);return true;
                        });
                        p.prepareAsync();
                    }catch(Exception e){
                        stopPlayback();overlay.setVisibility(View.VISIBLE);toast("Ошибка воспроизведения MP4");
                    }
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int w,int h){}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){
                    if(activeTexture==texture)stopPlayback();return true;
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface){}
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

    private int darken(int color){return Color.rgb(Math.round(Color.red(color)*.8f),Math.round(Color.green(color)*.8f),Math.round(Color.blue(color)*.8f));}
    private String colorKey(String name){return "sender_"+activeKey+"_"+MessageDb.normalizedAuthor(name);}
    private int authorColor(String name){
       int idx=((name==null?0:name.hashCode())&Integer.MAX_VALUE)%participantColors.length;
       return getPreferences(MODE_PRIVATE).getInt(colorKey(name),name!=null&&name.equalsIgnoreCase(mine)?OUTGOING:participantColors[idx]);
    }

    private void openPhotoGallery(MessageDb.Attachment initial){
        Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(0xff090e16);
        ImageView picture=new ImageView(this);picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(picture,new FrameLayout.LayoutParams(-1,-1));
        TextView caption=text("Фото",14,FG);caption.setGravity(Gravity.CENTER);
        frame.addView(caption,new FrameLayout.LayoutParams(-1,px(52),Gravity.TOP));
        Button close=btn("✕");frame.addView(close,new FrameLayout.LayoutParams(px(48),px(48),Gravity.TOP|Gravity.END));
        close.setOnClickListener(v->dialog.dismiss());
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);
        LinearLayout thumbsRow=row();thumbsRow.setPadding(px(4),px(4),px(4),px(4));strip.addView(thumbsRow);
        FrameLayout.LayoutParams film=new FrameLayout.LayoutParams(-1,px(83),Gravity.BOTTOM);frame.addView(strip,film);
        final MessageDb.Attachment[] current={initial};
        final int[] generation={0};
        final java.util.function.Consumer<MessageDb.Attachment>[] show=new java.util.function.Consumer[1];
        show[0]=item->{
            current[0]=item;int id=++generation[0];picture.setScaleX(1f);picture.setScaleY(1f);
            caption.setText(day(item.date)+" · "+(item.author==null?"Фото":item.author));
            DocumentFolder source=folder;
            if(source==null)return;
            mediaWorker.execute(()->{
                Bitmap bitmap=null;
                try{
                    Uri uri=source.resolve(item.path);
                    if(uri!=null){
                        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                        try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
                        BitmapFactory.Options opts=new BitmapFactory.Options();int scale=1;
                        while(bounds.outWidth/scale>2400||bounds.outHeight/scale>2400)scale*=2;
                        opts.inSampleSize=scale;
                        try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,opts);}
                    }
                }catch(Exception ignored){}
                Bitmap loaded=bitmap;
                runOnUiThread(()->{
                    if(id!=generation[0]||!dialog.isShowing())return;
                    picture.animate().cancel();picture.setAlpha(0f);picture.setTranslationX(px(12));
                    if(loaded!=null)picture.setImageBitmap(loaded);
                    picture.animate().alpha(1f).translationX(0).setDuration(230).setInterpolator(new DecelerateInterpolator()).start();
                });
            });
            worker.execute(()->{
                ArrayList<MessageDb.Attachment> older=new ArrayList<>(),newer=new ArrayList<>();
                MessageDb.Attachment cursor=item;
                for(int i=0;i<5;i++){cursor=db.adjacentMedia("image",cursor.messageId,cursor.id,false);if(cursor==null)break;older.add(0,cursor);}
                cursor=item;
                for(int i=0;i<5;i++){cursor=db.adjacentMedia("image",cursor.messageId,cursor.id,true);if(cursor==null)break;newer.add(cursor);}
                ArrayList<MessageDb.Attachment> window=new ArrayList<>(older);window.add(item);window.addAll(newer);
                runOnUiThread(()->{
                    if(id!=generation[0]||!dialog.isShowing())return;
                    thumbsRow.removeAllViews();
                    for(MessageDb.Attachment photo:window){
                        ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        LinearLayout.LayoutParams p=p(65,65);p.setMargins(px(3),0,px(3),0);thumbsRow.addView(thumb,p);
                        thumb.setBackground(shape(photo.id==item.id?ACCENT:PANEL,7));
                        thumb.setPadding(px(photo.id==item.id?3:1),px(photo.id==item.id?3:1),px(photo.id==item.id?3:1),px(photo.id==item.id?3:1));
                        loadThumbnail(photo.path,"image",thumb);
                        thumb.setOnClickListener(v->show[0].accept(photo));
                    }
                    strip.post(()->strip.smoothScrollTo(Math.max(0,(older.size()-2)*px(71)),0));
                });
            });
        };
        final android.view.ScaleGestureDetector photoZoom=new android.view.ScaleGestureDetector(this,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
            float scale=1f;
            @Override public boolean onScale(android.view.ScaleGestureDetector d){
                scale=Math.max(1f,Math.min(4f,scale*d.getScaleFactor()));
                picture.setScaleX(scale);picture.setScaleY(scale);return true;
            }
        });
        picture.setOnTouchListener(new View.OnTouchListener(){
            float x,y;
            public boolean onTouch(View v,MotionEvent e){
                photoZoom.onTouchEvent(e);
                if(e.getPointerCount()>1)return true;
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_UP){
                    float dx=e.getX()-x;
                    if(picture.getScaleX()<=1.01f&&Math.abs(dx)>px(55)&&Math.abs(dx)>Math.abs(e.getY()-y)){
                        picture.animate().translationX(dx<0?-px(85):px(85)).alpha(0f).setDuration(150).withEndAction(()->{
                            MessageDb.Attachment old=current[0];
                            worker.execute(()->{
                                MessageDb.Attachment next=db.adjacentMedia("image",old.messageId,old.id,dx<0);
                                runOnUiThread(()->{if(next!=null&&dialog.isShowing())show[0].accept(next);else{picture.setAlpha(1);picture.setTranslationX(0);}});
                            });
                        }).start();
                    }return true;
                }return true;
            }
        });
        dialog.setContentView(frame);dialog.show();show[0].accept(initial);
    }
    private void groupedParticipantColors(){
        worker.execute(()->{
            List<String> authors=db.participants();
            runOnUiThread(()->{
                if(authors.isEmpty()){toast("Сначала импортируй сообщения");return;}
                String[] names=authors.toArray(new String[0]);
                boolean[] checked=new boolean[names.length];
                new AlertDialog.Builder(this).setTitle("Объединить цвета участников")
                    .setMultiChoiceItems(names,checked,(d,index,value)->checked[index]=value)
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Выбрать цвет",(d,w)->{
                        ArrayList<String> chosen=new ArrayList<>();
                        for(int i=0;i<checked.length;i++)if(checked[i])chosen.add(names[i]);
                        if(chosen.isEmpty()){toast("Не выбраны участники");return;}
                        String[] colors={"Голубой","Сиреневый","Бирюзовый","Коричневый","Синий","Розовый","Оливковый","Стальной"};
                        new AlertDialog.Builder(this).setTitle("Одинаковый цвет для "+chosen.size()+" имён")
                            .setItems(colors,(dlg,pos)->{
                                android.content.SharedPreferences.Editor edit=getPreferences(MODE_PRIVATE).edit();
                                for(String name:chosen)edit.putInt(colorKey(name),participantColors[pos]);
                                edit.apply();showPage();
                            }).show();
                    }).show();
            });
        });
    }
    private void participantSettings(){
       worker.execute(()->{List<String> authors=db.participants();runOnUiThread(()->{
          if(authors.isEmpty()){toast("Сначала импортируй сообщения");return;}
          new AlertDialog.Builder(this).setTitle("Цвета участников").setItems(authors.toArray(new String[0]),(dlg,which)->pickAuthorColor(authors.get(which))).show();
       });});
    }
    private void pickAuthorColor(String name){
       String[] names={"Голубой","Сиреневый","Бирюзовый","Коричневый","Синий","Розовый","Оливковый","Стальной","Сброс"};
       new AlertDialog.Builder(this).setTitle(name).setItems(names,(d,which)->{
          android.content.SharedPreferences.Editor edit=getPreferences(MODE_PRIVATE).edit();
          if(which==participantColors.length)edit.remove(colorKey(name));else edit.putInt(colorKey(name),participantColors[which]);edit.apply();showPage();
       }).show();
    }
    /** Always register the media AFTER startInlineRound, because it resets the previous player. */
    private void startRoundPlayback(MessageDb.Attachment attachment,FrameLayout target,ImageView poster,TextView overlay){
        startInlineRound(attachment.path,target,poster,overlay);
        playingAttachment=attachment;
        inlineHolder=target;
        if(playerStrip!=null){
            playerStrip.setVisibility(View.VISIBLE);
            playerText.setText("◉ "+(attachment.author==null||attachment.author.trim().isEmpty()?"Видеосообщение":attachment.author));
            playerPause.setText("⏸");
        }
    }
    private void toggleActivePlayback(){
        if(player==null)return;
        try{if(player.isPlaying()){player.pause();playerPause.setText("▶");}else{player.start();playerPause.setText("⏸");}}catch(Exception ignored){}
    }
    private void playAdjacent(boolean forward){
        if(playingAttachment==null)return;
        final MessageDb.Attachment current=playingAttachment;
        worker.execute(()->{
            MessageDb.Attachment found=db.adjacentMedia(current.kind,current.messageId,current.id,forward);
            runOnUiThread(()->{if(found==null){toast("Других сообщений нет");return;}
                if(found.kind.equals("round"))showFloatingRound(found);
                else if(found.kind.equals("voice")||found.kind.equals("audio")){
                    startAudio(found.path,playerPause,playerText);
                    playingAttachment=found;playerStrip.setVisibility(View.VISIBLE);
                }
            });
        });
    }
    private void settingsMenu(){new AlertDialog.Builder(this).setTitle("Настройки").setItems(
       new String[]{"Моё имя (сообщения справа)","Цвета участников","Объединить цвета нескольких имён","Мои архивы","Создать резервную копию","Восстановить резервную копию"},(d,i)->{
          if(i==0)identityDialog();else if(i==1)participantSettings();else if(i==2)groupedParticipantColors();else if(i==3)chatPicker();
          else if(i==4)backupPicker();else restorePicker();
       }).show();}
    private void backupPicker(){
        new AlertDialog.Builder(this).setTitle("Резервная копия").setMessage("В ZIP сохраняются тексты всех чатов, избранные и закреплённые сообщения, заметки и настройки. Видео, фото и аудио не копируются: исходные папки Telegram нужно хранить отдельно. Копия не шифруется — сохраняй её в надёжном месте.")
            .setNegativeButton("Отмена",null).setPositiveButton("Выбрать место",(d,w)->{
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);
                i.putExtra(Intent.EXTRA_TITLE,"TelegramArchive-backup.zip");startActivityForResult(i,41);
            }).show();
    }
    private void restorePicker(){
        new AlertDialog.Builder(this).setTitle("Восстановление").setMessage("Архивы добавятся как новые, существующие чаты, избранное и закрепления сохранятся. Для медиа понадобится заново выбрать исходные папки. Используй только доверенные копии.")
            .setNegativeButton("Отмена",null).setPositiveButton("Выбрать ZIP",(d,w)->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,42);
            }).show();
    }
    private void backupAction(Uri uri,boolean restore){
        if(restoringBackup||importing)return;restoringBackup=true;
        status.setText(restore?"Восстанавливаю…":"Создаю резервную копию…");
        worker.execute(()->{
            try{
                SharedPreferences prefs=getPreferences(MODE_PRIVATE);
                if(restore){
                    int chats=ArchiveBackup.restore(this,prefs,uri,message->runOnUiThread(()->status.setText(message)));
                    runOnUiThread(()->{restoringBackup=false;loadArchiveRegistry();home=true;screen="chat";showPage();toast("Восстановлено чатов: "+chats);});
                }else{
                    saveReadingProgress();
                    ArchiveBackup.write(this,prefs,uri,message->runOnUiThread(()->status.setText(message)));
                    runOnUiThread(()->{restoringBackup=false;toast("Резервная копия сохранена");});
                }
            }catch(Exception e){runOnUiThread(()->{restoringBackup=false;status.setText("Ошибка резервной копии: "+e.getMessage());});}
        });
    }
    private final class VerticalTimeline extends View {
       private final Paint brush=new Paint(3);private int position,count;
       VerticalTimeline(){super(MainActivity.this);}
       void setPosition(int p,int c){position=p;count=c;invalidate();}
       @Override protected void onDraw(Canvas c){
         super.onDraw(c);float x=getWidth()-px(10),top=px(13),bottom=getHeight()-px(13);
         brush.setColor(0xff42657f);brush.setStrokeWidth(px(4));c.drawLine(x,top,x,bottom,brush);
         float ratio=count<=1?0:(float)position/(count-1);brush.setColor(ACCENT);c.drawCircle(x,top+(bottom-top)*ratio,px(5),brush);
       }
       @Override public boolean onTouchEvent(MotionEvent e){
         if(!screen.equals("chat"))return false;
         if(e.getActionMasked()==MotionEvent.ACTION_UP){
           float frac=Math.max(0,Math.min(1,(e.getY()-px(13))/Math.max(1,getHeight()-px(26))));
           chatOffset=Math.round(frac*Math.max(0,count-1));showPage();performClick();return true;
         }
         return true;
       }
       @Override public boolean performClick(){super.performClick();return true;}
    }
    private void openRoundPlayer(MessageDb.Attachment a){
       resolveMedia(a.path,uri->{
          Intent intent=new Intent(this,RoundPlayerActivity.class);
          intent.setData(uri);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          try{startActivity(intent);}catch(Exception e){toast("Не удалось открыть видео-сообщение");}
       });
    }
    private void closeFloatingRound(){
       if(floatingPanel!=null){FrameLayout p=floatingPanel;floatingPanel=null;stopPlayback();root.removeView(p);}
    }
    private void showFloatingRound(MessageDb.Attachment a){
       int resume=floatingStart;floatingStart=0;
       closeFloatingRound();
       FrameLayout panel=new FrameLayout(this);floatingPanel=panel;
       FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(px(157),px(160),Gravity.END|Gravity.BOTTOM);
       pos.rightMargin=px(12);pos.bottomMargin=px(105);root.addView(panel,pos);
       FrameLayout round=new FrameLayout(this);round.setBackground(shape(CHIP,100));
       round.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline o){o.setOval(0,0,view.getWidth(),view.getHeight());}});
       round.setClipToOutline(true);
       panel.addView(round,new FrameLayout.LayoutParams(px(140),px(140),Gravity.BOTTOM|Gravity.END));
       ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);round.addView(preview,new FrameLayout.LayoutParams(-1,-1));loadThumbnail(a.path,"round",preview);
       TextView play=text("▶",32,FG);play.setGravity(Gravity.CENTER);round.addView(play,new FrameLayout.LayoutParams(-1,-1));
       round.setOnClickListener(v->startInlineRound(a.path,round,preview,play));
       round.setOnLongClickListener(v->{mediaActions(a);return true;});
       TextView close=text("✕",20,FG);close.setBackground(shape(0xffa43b47,100));close.setGravity(Gravity.CENTER);
       panel.addView(close,new FrameLayout.LayoutParams(px(35),px(35),Gravity.TOP|Gravity.START));close.setOnClickListener(v->closeFloatingRound());
       panel.setOnTouchListener(new View.OnTouchListener(){float x,y,startX,startY;
         public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();startX=v.getTranslationX();startY=v.getTranslationY();return true;}
           if(e.getActionMasked()==MotionEvent.ACTION_MOVE){v.setTranslationX(startX+e.getRawX()-x);v.setTranslationY(startY+e.getRawY()-y);return true;}return false;}
       });
       startRoundPlayback(a,round,preview,play);
       handler.postDelayed(()->{try{if(player!=null&&resume>0)player.seekTo(resume);}catch(Exception ignored){}},650);
    }

    @Override protected void onPause(){super.onPause();stopPlayback();}
    @Override protected void onDestroy(){stopPlayback();worker.execute(()->db.close());worker.shutdown();mediaWorker.shutdown();super.onDestroy();}
}
