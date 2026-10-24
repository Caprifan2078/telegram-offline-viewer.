package org.openarchive.telegram;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HelpScreenTest {
    @Rule public ActivityTestRule<HelpActivity> rule=new ActivityTestRule<>(HelpActivity.class);
    @Test public void offlineGuideExistsAndHasSearch() throws Exception{
        String text;
        try(InputStream in=rule.getActivity().getAssets().open("help_ru.txt")){
            byte[] bytes=new byte[16384];int n=in.read(bytes);
            assertTrue(n>500);
            text=new String(bytes,0,n,StandardCharsets.UTF_8);
        }
        assertTrue(text.contains("## "));
        assertTrue(text.contains("коллекц"));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            View screen=rule.getActivity().getWindow().getDecorView();
            assertTrue(hasText(screen,"Помощь и инструкции"));
            assertTrue(hasEditText(screen));
        });
    }
    private boolean hasEditText(View view){
        if(view instanceof EditText)return true;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(hasEditText(group.getChildAt(i)))return true;
        }
        return false;
    }
    private boolean hasText(View view,String expected){
        if(view instanceof TextView&&expected.equals(((TextView)view).getText().toString()))return true;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),expected))return true;
        }
        return false;
    }
}
