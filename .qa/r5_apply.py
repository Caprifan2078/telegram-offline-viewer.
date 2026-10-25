#!/usr/bin/env python3
from pathlib import Path
root=Path.cwd()
build=root/"app/build.gradle"
text=build.read_text(encoding="utf-8")
needle="    compileOptions {\n        sourceCompatibility JavaVersion.VERSION_17"
replace="""    buildTypes {
        compactQa {
            initWith debug
            applicationIdSuffix '.compact'
            versionNameSuffix '-compact'
            matchingFallbacks = ['debug']
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.debug
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17"""
assert text.count(needle)==1
assert text.count("versionCode 38")==1 and text.count("versionName '0.38-qa-r4'")==1
text=text.replace("versionCode 38","versionCode 39").replace("versionName '0.38-qa-r4'","versionName '0.39-qa-r5'")
build.write_text(text.replace(needle,replace),encoding="utf-8")
(root/"app/proguard-rules.pro").write_text("""# Conservative R8 first pass: keep application names/models while shrinking
# unused third-party code. Relax only after Android device regression.
-keep class org.openarchive.telegram.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
# Okio references the optional JSR-305 Nullable marker at compile time.
-dontwarn javax.annotation.Nullable
""",encoding="utf-8")
pkg=root/"app/src/main/java/org/openarchive/telegram"
(pkg/"ConversationGapNavigator.java").write_text("""package org.openarchive.telegram;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
/** Local, bounded list of quiet periods; it cannot prove an export is missing. */
final class ConversationGapNavigator {
 static final int MAX_SCAN=100000,TOP=12,MIN_DAYS=7;
 static final class Gap {
  final LocalDate from,to;
  final long fromId,toId,days;
  Gap(LocalDate a,LocalDate b,long ai,long bi,long n){from=a;to=b;fromId=ai;toId=bi;days=n;}
 }
 private final ArrayList<Gap> top=new ArrayList<>();
 private LocalDate previous;
 private long previousId;
 private int scanned,invalid,backwards;
 void accept(long id,LocalDate day){
  scanned++;
  if(day==null||id<=0){invalid++;return;}
  if(previous!=null){
   long d=ChronoUnit.DAYS.between(previous,day);
   if(d<0){backwards++;return;}
   if(d>=MIN_DAYS){
    top.add(new Gap(previous,day,previousId,id,d));
    top.sort(Comparator.comparingLong((Gap g)->g.days).reversed().thenComparing(g->g.from));
    if(top.size()>TOP)top.remove(top.size()-1);
   }
  }
  previous=day;previousId=id;
 }
 int scanned(){return scanned;} int invalid(){return invalid;}
 int outOfOrder(){return backwards;}
 List<Gap> gaps(){return Collections.unmodifiableList(new ArrayList<>(top));}
}
""",encoding="utf-8")
main=pkg/"MainActivity.java"
src=main.read_text(encoding="utf-8")
point="    /** Drawer sections expose one clear destination per action and avoid repeating settings. */"
method=r"""    /** Read-only conversation pauses. Quiet days are not evidence of missing messages. */
    private void showConversationPauses(){
        final String chatKey=activeKey;
        final MessageDb source=db;
        if(source==null){toast("Сначала открой переписку");return;}
        status.setText("Ищу паузы в истории переписки…");
        worker.execute(()->{
            final ConversationGapNavigator scan=new ConversationGapNavigator();
            String error=null;
            try(android.database.Cursor c=source.getReadableDatabase().rawQuery(
                    "SELECT id,date_text FROM messages ORDER BY id ASC LIMIT "+
                        (ConversationGapNavigator.MAX_SCAN+1),null)){
                while(c.moveToNext()&&scan.scanned()<ConversationGapNavigator.MAX_SCAN){
                    ChatTrends.Moment moment=ChatTrends.parse(c.getString(1));
                    scan.accept(c.getLong(0),moment==null?null:moment.day);
                }
            }catch(Exception e){error=e.getClass().getSimpleName();}
            final String failure=error;
            runOnUiThread(()->{
                if(isFinishing()||home||!chatKey.equals(activeKey))return;
                if(failure!=null){status.setText("Ошибка просмотра пауз: "+failure);return;}
                final java.util.List<ConversationGapNavigator.Gap> gaps=scan.gaps();
                final String[] items=new String[gaps.size()];
                for(int i=0;i<gaps.size();i++){
                    ConversationGapNavigator.Gap g=gaps.get(i);
                    items[i]=g.days+" дней · "+g.from+" → "+g.to;
                }
                String intro="Паузы между сообщениями, не доказательство пропусков экспорта. " +
                    "Проверено записей: "+scan.scanned()+"; дат без распознавания: "+scan.invalid()+
                    "; с обратным порядком: "+scan.outOfOrder()+"."+
                    (scan.scanned()>=ConversationGapNavigator.MAX_SCAN?
                        "\nПоказан лишь первый участок большого архива.":"");
                new AlertDialog.Builder(this).setTitle("Навигатор пауз")
                    .setMessage(intro+(items.length==0?"\nПаузы от 7 дней не найдены.":""))
                    .setItems(items,(dialog,index)->{
                        ConversationGapNavigator.Gap g=gaps.get(index);
                        new AlertDialog.Builder(this).setTitle("Границы паузы")
                            .setMessage(g.from+" → "+g.to+"\nПерейти к сообщению до или после паузы?")
                            .setPositiveButton("После",(d,w)->jump(g.toId,""))
                            .setNeutralButton("До",(d,w)->jump(g.fromId,""))
                            .setNegativeButton("Отмена",null).show();
                    }).setPositiveButton("Закрыть",null).show();
                status.setText("Навигатор пауз: просмотр завершён");
            });
        });
    }

"""
menu="""            sideMenuItem(group,dialog,pane,"◎  Здоровье медиатеки","Проверка отсутствующих файлов",()->{
                if(home)toast("Сначала открой переписку");else showMediaHealth();});"""
assert src.count(point)==1 and src.count(menu)==1
src=src.replace(point,method+point).replace(menu,menu+"""
            sideMenuItem(group,dialog,pane,"◌  Навигатор пауз","Промежутки в истории · без изменения файлов",()->{
                if(home)toast("Сначала открой переписку");else showConversationPauses();});""")
main.write_text(src,encoding="utf-8")
(root/"app/src/test/java/org/openarchive/telegram/ConversationGapNavigatorTest.java").write_text("""package org.openarchive.telegram;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;
public class ConversationGapNavigatorTest {
 @Test public void findsLongestPauses(){
  ConversationGapNavigator n=new ConversationGapNavigator();
  n.accept(1,LocalDate.of(2021,1,1));
  n.accept(2,LocalDate.of(2021,1,3));
  n.accept(3,LocalDate.of(2021,1,13));
  n.accept(4,LocalDate.of(2021,2,13));
  assertEquals(2,n.gaps().size());
  assertEquals(31L,n.gaps().get(0).days);
  assertEquals(3L,n.gaps().get(0).fromId);
 }
 @Test public void invalidDatesAreNotGaps(){
  ConversationGapNavigator n=new ConversationGapNavigator();
  n.accept(1,LocalDate.of(2022,1,4));
  n.accept(2,null);
  n.accept(3,LocalDate.of(2021,1,1));
  n.accept(4,LocalDate.of(2022,1,5));
  assertEquals(1,n.invalid());assertEquals(1,n.outOfOrder());
  assertTrue(n.gaps().isEmpty());
 }
 @Test public void boundedAndImmutable(){
  ConversationGapNavigator n=new ConversationGapNavigator();
  for(int i=0;i<50;i++)n.accept(i+1,LocalDate.of(2018,1,1).plusDays(i*8L));
  assertEquals(12,n.gaps().size());
  try{n.gaps().clear();fail("mutable");}catch(UnsupportedOperationException ok){}
 }
}
""",encoding="utf-8")
print("R5 source patch applied")
