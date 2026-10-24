package org.openarchive.telegram;

import android.app.Activity;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searchable in-app help. Offline; no permissions, analytics or telemetry. */
public class HelpActivity extends Activity {
    private static final int BG=0xff0b1424,FG=0xfff5f9ff,MUTED=0xffbbcbdc,ACCENT=0xff7ad2ff;
    private final List<Section> sections=new ArrayList<>();
    private LinearLayout cards;
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static final class Section {
        final String title,body;
        Section(String title,String body){this.title=title;this.body=body;}
    }
    private GradientDrawable surface(int color,int radius){
        GradientDrawable bg=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,new int[]{color,0xff1c3850});
        bg.setCornerRadius(dp(radius));bg.setStroke(dp(1),0x667aa8bc);
        return bg;
    }
    private TextView label(String txt,int size,int color){
        TextView t=new TextView(this);t.setText(txt);t.setTextSize(size);t.setTextColor(color);
        return t;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        try(BufferedReader in=new BufferedReader(
                new InputStreamReader(getAssets().open("help_ru.txt"),"UTF-8"))){
            String line,title="Первые шаги";StringBuilder body=new StringBuilder();
            while((line=in.readLine())!=null){
                if(line.startsWith("## ")){
                    if(body.length()>0)sections.add(new Section(title,body.toString().trim()));
                    title=line.substring(3).trim();body.setLength(0);
                }else if(!line.startsWith("# "))body.append(line.replace("**","").replace("`","")).append('\n');
            }
            if(body.length()>0)sections.add(new Section(title,body.toString().trim()));
        }catch(Exception ex){
            sections.add(new Section("Справка недоступна","Не удалось прочитать встроенную инструкцию. Сообщения и медиа не затронуты."));
        }
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);root.setPadding(dp(12),dp(12),dp(12),dp(9));
        setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView back=label("‹",32,ACCENT);back.setContentDescription("Закрыть помощь");
        back.setGravity(Gravity.CENTER);header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v->finish());
        TextView heading=label("Помощь и инструкции",21,FG);heading.setTypeface(null,Typeface.BOLD);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView tag=label("ОФЛАЙН",11,ACCENT);header.addView(tag);
        TextView intro=label("Найди нужную функцию или открой раздел ниже. Все инструкции уже внутри приложения.",13,MUTED);
        intro.setPadding(dp(8),dp(8),dp(8),dp(13));root.addView(intro);
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setTextColor(FG);input.setHintTextColor(MUTED);
        input.setHint("Поиск: медиа, ZIP, коллекции, MP3…");input.setTextSize(15);
        input.setBackground(surface(0xff21354b,14));
        input.setPadding(dp(14),dp(11),dp(14),dp(11));
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,dp(49));
        searchParams.bottomMargin=dp(12);root.addView(input,searchParams);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        cards=new LinearLayout(this);cards.setOrientation(LinearLayout.VERTICAL);scroll.addView(cards);
        showSections("");
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){showSections(s.toString());}
            public void afterTextChanged(Editable e){}
        });
        TextView footer=label("Совет: не удаляй установленное приложение или единственную резервную копию ради тестовой APK.",12,MUTED);
        footer.setPadding(dp(7),dp(9),dp(5),dp(3));root.addView(footer);
    }
    private void showSections(String search){
        cards.removeAllViews();
        String q=search.trim().toLowerCase(Locale.ROOT);
        int matched=0;
        for(Section section:sections){
            if(!q.isEmpty()&&!((section.title+" "+section.body).toLowerCase(Locale.ROOT).contains(q)))continue;
            matched++;
            LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackground(surface(0xff192e46,16));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=dp(8);cards.addView(panel,lp);
            TextView head=label("▸  "+section.title,16,FG);
            head.setTypeface(null,Typeface.BOLD);head.setPadding(dp(15),dp(14),dp(12),dp(14));
            panel.addView(head);
            TextView explanation=label(section.body,14,MUTED);
            explanation.setPadding(dp(16),dp(0),dp(15),dp(17));
            explanation.setVisibility(q.isEmpty()?View.GONE:View.VISIBLE);
            panel.addView(explanation);
            if(!q.isEmpty())head.setText("▾  "+section.title);
            head.setOnClickListener(v->{
                boolean open=explanation.getVisibility()!=View.VISIBLE;
                explanation.setVisibility(open?View.VISIBLE:View.GONE);
                head.setText((open?"▾  ":"▸  ")+section.title);
            });
        }
        if(matched==0){
            TextView none=label("Ничего не найдено. Попробуй другой запрос.",14,MUTED);
            none.setPadding(dp(14),dp(12),dp(12),dp(12));cards.addView(none);
        }
    }
}
