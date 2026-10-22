from pathlib import Path
p=Path("app/src/main/java/org/offline/tgviewer/MainActivity.java")
s=p.read_text()
duplicate='''    @Override protected void onDestroy() {
        if (media != null) media.stopPlayback();
        io.shutdownNow();
        super.onDestroy();
    }

'''
assert duplicate in s
s=s.replace(duplicate,"",1)
p.write_text(s)
q=Path("app/src/main/java/org/offline/tgviewer/SafeMediaView.java")
s=q.read_text()
s=s.replace("import android.view.Gravity;","import android.view.Gravity;\nimport static android.view.ViewGroup.LayoutParams.MATCH_PARENT;\nimport static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;")
q.write_text(s)
