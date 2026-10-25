#!/usr/bin/env python3
"""R6 premium offline UI and hierarchical help; apply after trusted R5 patch."""
from pathlib import Path
root=Path.cwd()
pkg=root/'app/src/main/java/org/openarchive/telegram'
main=pkg/'MainActivity.java'
helpfile=pkg/'HelpContent.java'
s=main.read_text(encoding='utf-8')
h=helpfile.read_text(encoding='utf-8')
def sub(before,after,label):
 global s
 count=s.count(before)
 if count!=1: raise RuntimeError(f'{label}: expected one anchor, got {count}')
 s=s.replace(before,after,1)

# Make all button surfaces consistent without installing expensive blurs.
sub('''        b.setBackground(new RippleDrawable(ColorStateList.valueOf(themeIndex==2?0x26336c9c:0x387ad2ff),
            outlined(PANEL,14,false),null));
        return b;''','''        b.setBackground(new RippleDrawable(ColorStateList.valueOf(themeIndex==2?0x22336c9c:0x397ad2ff),
            outlined(PANEL,18,false),shape(0xffffffff,18)));
        if(motionDuration(120)>0){
            android.animation.StateListAnimator states=new android.animation.StateListAnimator();
            android.animation.AnimatorSet pressed=new android.animation.AnimatorSet();
            pressed.playTogether(android.animation.ObjectAnimator.ofFloat(b,View.SCALE_X,.975f),
                android.animation.ObjectAnimator.ofFloat(b,View.SCALE_Y,.975f));
            pressed.setDuration(motionDuration(105));
            android.animation.AnimatorSet released=new android.animation.AnimatorSet();
            released.playTogether(android.animation.ObjectAnimator.ofFloat(b,View.SCALE_X,1f),
                android.animation.ObjectAnimator.ofFloat(b,View.SCALE_Y,1f));
            released.setDuration(motionDuration(175));
            states.addState(new int[]{android.R.attr.state_pressed},pressed);
            states.addState(new int[]{},released);
            b.setStateListAnimator(states);
        }
        return b;''','uniform buttons')

# More visible primary CTA without altering click behavior.
sub('''        add.setBackground(new RippleDrawable(ColorStateList.valueOf(0x436fc6f4),outlined(CHIP,17,true),null));''','''        add.setTextColor(themeIndex==2?0xff193852:0xffe7f8ff);
        add.setBackground(new RippleDrawable(ColorStateList.valueOf(0x66ffffff),
            gradient(themeIndex==2?0xffc2e8f7:0xff12628a,themeIndex==2?0xff81d0f0:0xff17425d,20),
            shape(0xffffffff,20)));
        add.setContentDescription("Добавить переписку из экспорта Telegram");''','primary action')

# Old help: 27 accordion rows. New: categories -> shelves -> individual illustrated chapter.
start=s.index('    // The guide is a real in-app screen, not a chain of system dialogs.')
end=s.index('    private void animateEntrance(){',start)
newhelp='''    // Explicit navigation stack: category -> shelf -> one chapter. No giant accordion.
    private boolean helpReturnHome=true;
    private String helpReturnScreen="chat";
    private int helpCategory=-1,helpShelf=-1,helpChapter=-1;
    private void showHelp(){
        if(!"help".equals(screen)){
            helpReturnHome=home;
            helpReturnScreen=screen;
        }
        helpCategory=-1;helpShelf=-1;helpChapter=-1;
        screen="help";renderHelpPage();
    }
    private boolean helpStepBack(){
        if(helpChapter!=-1){helpChapter=-1;renderHelpPage();return true;}
        if(helpShelf!=-1){helpShelf=-1;renderHelpPage();return true;}
        if(helpCategory!=-1){helpCategory=-1;renderHelpPage();return true;}
        return false;
    }
    private void leaveHelp(){
        if(helpStepBack())return;
        ++loadToken;
        home=helpReturnHome;
        screen=helpReturnScreen;
        showPage();
    }
    private void helpNavCard(String header,String subtitle,Runnable action){
        LinearLayout box=column();box.setPadding(px(17),px(14),px(17),px(15));
        box.setBackground(new RippleDrawable(ColorStateList.valueOf(0x447ad2ff),
            outlined(PANEL,19,false),shape(0xffffffff,19)));
        LinearLayout.LayoutParams lp=p(-1,-2);lp.setMargins(px(7),px(4),px(7),px(7));list.addView(box,lp);
        TextView label=text(header+"   ›",16,FG);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));box.addView(label);
        if(subtitle!=null&&!subtitle.isEmpty()){
            TextView explanation=text(subtitle,12,SUB);explanation.setPadding(0,px(6),0,0);box.addView(explanation);
        }
        box.setContentDescription(header+". "+subtitle);
        box.setOnClickListener(v->{action.run();});
    }
    private void renderHelpPage(){
        ++loadToken;
        list.removeAllViews();list.setBackgroundColor(BG);
        title.setText("Помощь · Telegram Archive");
        subtitle.setText("Пошаговая навигация · офлайн");
        menuButton.setVisibility(View.GONE);navBack.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.GONE);bottomPager.setVisibility(View.GONE);
        if(headerActions!=null)headerActions.setVisibility(View.GONE);
        savedComposer.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        playerStrip.setVisibility(View.GONE);pinnedBar.removeAllViews();
        filterBar.removeAllViews();searchArea.removeAllViews();
        status.setText("Справочник не изменяет твои данные");
        TextView crumb=text(helpChapter!=-1?"ИНСТРУКЦИЯ":helpShelf!=-1?"ПОДРАЗДЕЛ":helpCategory!=-1?"РАЗДЕЛ":"СПРАВОЧНИК",11,ACCENT);
        crumb.setTypeface(null,Typeface.BOLD);crumb.setPadding(px(12),px(16),0,px(5));list.addView(crumb);
        if(helpCategory!=-1){
            Button back=btn("‹ Назад к "+(helpChapter!=-1?"подразделу":helpShelf!=-1?"разделу":"всем темам"));
            LinearLayout.LayoutParams backLp=p(-1,45);backLp.setMargins(px(7),px(4),px(7),px(10));list.addView(back,backLp);
            back.setOnClickListener(v->helpStepBack());
        }
        if(helpChapter!=-1){
            HelpContent.Chapter chapter=HelpContent.all().get(helpChapter-1);
            TextView heading=text(chapter.title,21,FG);heading.setTypeface(null,Typeface.BOLD);
            heading.setPadding(px(14),px(7),px(12),px(12));list.addView(heading);
            TextView note=text("Инструкция для офлайн-версии. Выполняй шаги по порядку; если пункт отсутствует, проверь версию приложения.",12,SUB);
            note.setPadding(px(14),0,px(13),px(13));list.addView(note);
            String[] paragraphs=chapter.description.split("\\n");
            for(String paragraph:paragraphs){
                TextView line=text(paragraph,15,FG);line.setLineSpacing(px(4),1.06f);
                line.setPadding(px(16),px(13),px(16),px(13));line.setBackground(glass(PANEL,17));
                LinearLayout.LayoutParams lp=p(-1,-2);lp.setMargins(px(7),px(4),px(7),px(8));list.addView(line,lp);
            }
        }else if(helpShelf!=-1){
            HelpCatalog.Shelf shelf=HelpCatalog.GROUPS[helpCategory].shelves[helpShelf];
            TextView heading=text(shelf.title,20,FG);heading.setPadding(px(12),px(7),px(12),px(12));list.addView(heading);
            for(int id:shelf.chapterIds){
                HelpContent.Chapter chapter=HelpContent.all().get(id-1);
                helpNavCard(chapter.title,"Открыть подробное объяснение",()->{helpChapter=id;renderHelpPage();});
            }
        }else if(helpCategory!=-1){
            HelpCatalog.Group group=HelpCatalog.GROUPS[helpCategory];
            TextView heading=text(group.title,20,FG);heading.setPadding(px(12),px(7),px(12),px(12));list.addView(heading);
            for(int i=0;i<group.shelves.length;i++){
                final int index=i;HelpCatalog.Shelf shelf=group.shelves[i];
                helpNavCard(shelf.title,shelf.subtitle,()->{helpShelf=index;renderHelpPage();});
            }
        }else{
            TextView heading=text("Один вопрос — один понятный маршрут",21,FG);
            heading.setTypeface(null,Typeface.BOLD);heading.setPadding(px(12),px(6),px(12),px(6));list.addView(heading);
            TextView intro=text("Выбери одну из тем. Внутри будут короткие подразделы, а подробные шаги откроются отдельно — без бесконечной ленты инструкций.",13,SUB);
            intro.setPadding(px(12),0,px(12),px(14));list.addView(intro);
            for(int i=0;i<HelpCatalog.GROUPS.length;i++){
                final int index=i;HelpCatalog.Group group=HelpCatalog.GROUPS[i];
                helpNavCard(group.title,group.subtitle,()->{helpCategory=index;renderHelpPage();});
            }
            EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);
            search.setHint("Найти инструкцию…");search.setTextColor(FG);search.setHintTextColor(SUB);
            search.setBackground(glass(PANEL,16));search.setPadding(px(14),px(10),px(14),px(10));
            LinearLayout.LayoutParams searchLp=p(-1,-2);searchLp.setMargins(px(7),px(13),px(7),px(9));list.addView(search,searchLp);
            LinearLayout results=column();list.addView(results,p(-1,-2));
            search.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence t,int a,int b,int c){}
                public void onTextChanged(CharSequence t,int a,int b,int c){
                    results.removeAllViews();String q=t.toString().trim();if(q.isEmpty())return;
                    List<HelpContent.Chapter> matches=HelpContent.find(q);
                    for(HelpContent.Chapter chapter:matches){
                        int id=HelpContent.all().indexOf(chapter)+1;
                        LinearLayout card=column();card.setPadding(px(12),px(8),px(12),px(8));
                        TextView name=text("⌕ "+chapter.title,14,ACCENT);card.addView(name);
                        results.addView(card,p(-1,-2));
                        card.setOnClickListener(v->{int[] path=HelpCatalog.findChapter(id);
                            if(path==null)return;helpCategory=path[0];helpShelf=path[1];helpChapter=id;renderHelpPage();});
                    }
                    if(matches.isEmpty()){TextView empty=text("Совпадений нет",13,SUB);results.addView(empty);}
                }
                public void afterTextChanged(android.text.Editable e){}
            });
        }
        TextView privacy=text("Без интернета и отправки данных. Не удаляй исходный экспорт или старое приложение до проверки полной резервной копии.",12,SUB);
        privacy.setPadding(px(14),px(17),px(14),px(28));list.addView(privacy);
        scroll.post(()->scroll.scrollTo(0,0));
    }
'''
s=s[:start]+newhelp+s[end:]
# In-app hardware back traverses the same help stack.
assert 'if("help".equals(screen)){leaveHelp();return;}' in s

# Video: translucent polished controls fade on inactivity; one-tap returns controls.
sub('''        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);
        VideoView video=new VideoView(this);''','''        FrameLayout frame=new FrameLayout(this);frame.setBackground(gradient(0xff040910,0xff152338,0));
        VideoView video=new VideoView(this);''','video backdrop')
sub('''        bottom.setBackground(glass(0xe6192737,15));''','''        bottom.setBackground(glass(0xe812263b,21));''','video lower controls')
sub('''        header.setBackground(glass(0xe3192737,14));''','''        header.setBackground(glass(0xe812263b,21));''','video upper controls')
sub('''        final Runnable[] updater={null};
        Runnable refresh=''', '''        final Runnable[] updater={null};
        final Runnable[] autoHide={null};
        final boolean[] visible={true};
        final Runnable hideControls=()->{
            if(!dialog.isShowing()||!video.isPlaying()||!visible[0])return;
            visible[0]=false;
            header.animate().alpha(0f).setDuration(motionDuration(260)).withEndAction(()->header.setVisibility(View.GONE)).start();
            bottom.animate().alpha(0f).setDuration(motionDuration(260)).withEndAction(()->bottom.setVisibility(View.GONE)).start();
        };
        autoHide[0]=hideControls;
        video.setOnClickListener(v->{
            if(visible[0]){
                handler.removeCallbacks(hideControls);hideControls.run();
            }else{
                visible[0]=true;header.animate().cancel();bottom.animate().cancel();
                header.setVisibility(View.VISIBLE);bottom.setVisibility(View.VISIBLE);
                header.animate().alpha(1f).setDuration(motionDuration(200)).start();
                bottom.animate().alpha(1f).setDuration(motionDuration(200)).start();
                handler.postDelayed(hideControls,4500);
            }
        });
        Runnable refresh=''', 'video autohide')
sub('''            try{video.start();handler.post(updater[0]);}catch(Exception e){toast("Не удалось запустить видео");}''','''            try{video.start();handler.post(updater[0]);handler.postDelayed(autoHide[0],4500);}catch(Exception e){toast("Не удалось запустить видео");}''','video schedule')
sub('''        dialog.setOnDismissListener(d->{handler.removeCallbacks(updater[0]);prepared[0]=null;video.stopPlayback();});''','''        dialog.setOnDismissListener(d->{handler.removeCallbacks(updater[0]);handler.removeCallbacks(autoHide[0]);
            header.animate().cancel();bottom.animate().cancel();prepared[0]=null;video.stopPlayback();});''','video stop')
# Gallery improves tap targets and keeps adaptive playback/zoom intact.
sub('''        caption.setBackground(shape(0xaa17293e,13));''','''        caption.setBackground(glass(0xdb13283f,19));
        caption.setLetterSpacing(.035f);''','gallery caption')
sub('''        thumbsRow.setBackground(shape(0x9916263a,16));''','''        thumbsRow.setBackground(glass(0xcb152a41,22));''','gallery film')
main.write_text(s,encoding='utf-8')

# Rich step-by-step instructions for all post-R3 features and appearance updates.
needle='        ALL=Collections.unmodifiableList(chapters);'
assert h.count(needle)==1
chapters='''        chapters.add(new Chapter("28. Экспорт чата из Telegram на компьютер",
            "ВАЖНО: официальный массовый экспорт переписки с медиа выполняется через Telegram Desktop на Windows, macOS или Linux — не через наш просмотрщик.\\n"
            +"1) Открой Telegram Desktop и нужный чат. В меню чата ⋮ выбери экспорт истории чата, если он доступен. В некоторых версиях экспорт находится в настройках расширенных возможностей.\\n"
            +"2) Выбери HTML, отметь фотографии, видео, голосовые, кружки, документы и стикеры. Укажи достаточный предел размера вложений и полный диапазон дат.\\n"
            +"3) Дождись завершения экспорта и проверь папку: messages.html или несколько HTML-частей плюс подпапки с медиа. Проверь, что файлы реально лежат рядом.\\n"
            +"4) Перенеси всю папку целиком через USB или упакуй в ZIP/RAR. Нельзя переносить только HTML: в нём обычно ссылки на отдельные медиа.\\n"
            +"5) В Telegram Archive нажми «Добавить переписку» и выбери папку или архив. Сравни количество сообщений и открой несколько старых фото/видео.\\n"
            +"Файлы, которые Telegram Desktop не скачал или не включил в экспорт, просмотрщик восстановить не сможет. Мобильный Telegram и веб-версия могут не предоставлять эквивалентный полный экспорт. Не вводи пароль Telegram в сторонних экспортёрах."));
        chapters.add(new Chapter("29. Сравнение двух переписок",
            "1) На экране «Мои переписки» предварительно импортируй два разных чата.\\n"
            +"2) Нажми «Сравнение диалогов». Выбери первый и второй чат.\\n"
            +"3) Укажи год, чтобы сравнить активность по 12 месяцам и общий объём сообщений. Сверяй отдельно участников, избранное, фото, видео, голосовые, кружки и другие вложения.\\n"
            +"4) При необходимости открой «Воспоминания двух чатов», чтобы увидеть события на общей временной оси и переходить к оригиналам.\\n"
            +"Чаты не объединяются в один и не меняются. Доли и периоды не доказывают отношения между людьми. Сравнивай только известные тебе даты и полные экспорты."));
        chapters.add(new Chapter("30. Две переписки на одной линии воспоминаний",
            "1) Импортируй два чата, затем открой «Воспоминания двух чатов» со списка переписок или из раздела «Взаимоотношения».\\n"
            +"2) Выбери пару переписок, год и при желании месяц.\\n"
            +"3) Сообщения отображаются в общей хронологии; у каждого явно показано, к какому исходному чату оно относится.\\n"
            +"4) Нажми «Перейти к оригиналу», чтобы открыть это сообщение в его настоящей переписке. Изменение даты лишь фильтрует просмотр.\\n"
            +"Программа не объединяет участников и не предполагает связи между сообщениями из разных чатов. Для больших архивов вывод может быть ограничен — это не удаляет данные."));
        chapters.add(new Chapter("31. Навигатор пауз в переписке",
            "1) Открой нужный чат и в боковом меню ☰ выбери «Навигатор пауз».\\n"
            +"2) Программа локально проверит до 100 000 сообщений и найдёт до 12 самых длительных пауз не меньше семи суток.\\n"
            +"3) Нажми на паузу, затем выбери «До» или «После», чтобы перейти к сообщению на её границе.\\n"
            +"4) Если показано «частично», обрати внимание: рассмотрена лишь часть огромного архива.\\n"
            +"Пауза не означает потерю сообщений: собеседники могли просто не писать. Данные не изменяются, медиа не скачиваются."));
        chapters.add(new Chapter("32. Премиальный интерфейс и управление видео",
            "Кнопки во всех разделах используют согласованные скругления, световой отклик на нажатие и мягкие переходы. Внешний вид можно менять через ☰ → «Оформление и персонализация».\\n"
            +"Воспроизведение видео: открой ролик в «Медиа»; в полноэкранном режиме снизу — перемотка и пауза, сверху — назад и ⚙ со скоростью или внешним плеером. Одиночное нажатие на видео скрывает или возвращает управляющие панели. Во время просмотра панели могут скрыться автоматически.\\n"
            +"Для фотографий доступны увеличение двумя пальцами, сдвиг увеличенного кадра, свайп к соседней фотографии, миниатюры снизу.\\n"
            +"Чтобы уменьшить эффекты и расход батареи, открой настройки производительности и включи «Уменьшить анимации». Приложение также учитывает системную настройку анимаций Android. Обои и стиль кнопок не изменяют сохранённые сообщения."));
'''
h=h.replace(needle,chapters+needle)
helpfile.write_text(h,encoding='utf-8')

(pkg/'HelpCatalog.java').write_text('''package org.openarchive.telegram;
import java.util.HashSet;
/** Deterministic offline taxonomy, independent of Activity and private data. */
final class HelpCatalog {
 static final class Shelf {
  final String title,subtitle;final int[] chapterIds;
  Shelf(String title,String subtitle,int... ids){this.title=title;this.subtitle=subtitle;chapterIds=ids;}
 }
 static final class Group {
  final String title,subtitle;final Shelf[] shelves;
  Group(String title,String subtitle,Shelf... shelves){this.title=title;this.subtitle=subtitle;this.shelves=shelves;}
 }
 static final Group[] GROUPS={
  new Group("Первые шаги","Начни с импорта и безопасного знакомства с читалкой",
   new Shelf("Первый запуск","Начать с нуля и найти главные кнопки",1,16),
   new Shelf("Основные действия","Чаты, поиск и избранное",2),
   new Shelf("Проверка после обновления","Ничего не потерять при обновлении",20)),
  new Group("Экспорт и импорт","Четыре отдельных маршрута: от Telegram до нового телефона",
   new Shelf("Экспорт из Telegram","Что скачать и какие медиа включить",28),
   new Shelf("Импорт в Telegram Archive","Папка, ZIP, RAR и недостающие медиа",3),
   new Shelf("Резервные копии","Полная, компактная и капсула избранного",4,15),
   new Shelf("Переезд на новый телефон","Пошаговый перенос и проверка",27)),
  new Group("Диалоги и воспоминания","Сравнивай два чата, историю и статистику",
   new Shelf("Сравнение двух чатов","Показатели и общая шкала",29,30),
   new Shelf("Воспоминания и этапы","Личные события, этапы и повторяющиеся фразы",21,24,25,26),
   new Shelf("Поиск и графики","Статистика, активность и паузы",11,17,31),
   new Shelf("Цитаты и источники","Как сохранить часть текста и найти оригинал",18)),
  new Group("Медиа и плеер","Фото, видео, кружки, голосовые и коллекции",
   new Shelf("Просмотр медиа","Видео, GIF, стикеры и кинолента",5,22,23),
   new Shelf("Сохранение файлов","Одиночное и массовое сохранение, голосовые",6,7,10),
   new Shelf("Коллекции и дубликаты","Свои подборки и проверка SHA-256",8,9)),
  new Group("Оформление и безопасность","Красивый интерфейс, скорость и защита",
   new Shelf("Темы и кнопки","Настройки внешнего вида и новый видеоплеер",12,32),
   new Shelf("Жесты и плавность","Анимации, настройки памяти и навигация",13,19),
   new Shelf("Диагностика","Что сделать при сбое без потери архива",14))
 };
 static int[] findChapter(int id){
  for(int i=0;i<GROUPS.length;i++)for(int j=0;j<GROUPS[i].shelves.length;j++)
   for(int chapter:GROUPS[i].shelves[j].chapterIds)if(chapter==id)return new int[]{i,j};
  return null;
 }
 static boolean hasEveryChapter(){
  HashSet<Integer> seen=new HashSet<>();
  for(Group g:GROUPS)for(Shelf s:g.shelves)for(int id:s.chapterIds)seen.add(id);
  if(seen.size()!=HelpContent.all().size())return false;
  for(int i=1;i<=HelpContent.all().size();i++)if(!seen.contains(i))return false;
  return true;
 }
}
''',encoding='utf-8')
tests=root/'app/src/test/java/org/openarchive/telegram'
tests.mkdir(parents=True,exist_ok=True)
(tests/'HelpCatalogTest.java').write_text('''package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class HelpCatalogTest {
 @Test public void everyChapterReachable(){assertTrue(HelpCatalog.hasEveryChapter());}
 @Test public void exportIsItsOwnSection(){
   int[] p=HelpCatalog.findChapter(28);assertNotNull(p);
   assertEquals("Экспорт из Telegram",HelpCatalog.GROUPS[p[0]].shelves[p[1]].title);
 }
 @Test public void noInvalidIndices(){
   for(HelpCatalog.Group g:HelpCatalog.GROUPS)for(HelpCatalog.Shelf s:g.shelves)
    for(int id:s.chapterIds)assertTrue(id>=1&&id<=HelpContent.all().size());
 }
 @Test public void comparisonAndPausesDocumented(){
   assertNotNull(HelpCatalog.findChapter(29));assertNotNull(HelpCatalog.findChapter(30));
   assertNotNull(HelpCatalog.findChapter(31));assertNotNull(HelpCatalog.findChapter(32));
 }
}
''',encoding='utf-8')
gradle=root/'app/build.gradle';b=gradle.read_text(encoding='utf-8')
assert "versionCode 39" in b and "0.39-qa-r5" in b
b=b.replace("versionCode 39","versionCode 40").replace("0.39-qa-r5","0.40-qa-r6")
gradle.write_text(b,encoding='utf-8')
print('R6 premium UX patch applied: help hierarchy, accessible chapters, buttons, media, version 0.40')