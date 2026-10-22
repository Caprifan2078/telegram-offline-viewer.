from pathlib import Path
p=Path('app/src/main/java/org/offline/tgviewer/SearchHub.java')
s=p.read_text()
s=s.replace('import android.widget.ArrayAdapter;', 'import android.widget.ArrayAdapter;\nimport android.widget.BaseAdapter;')
p.write_text(s)
