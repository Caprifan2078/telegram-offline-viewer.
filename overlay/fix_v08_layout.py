from pathlib import Path
p=Path('project/app/src/main/java/org/offline/tgviewer/SafeMediaView.java')
s=p.read_text()
s=s.replace('package org.offline.tgviewer;', 'package org.offline.tgviewer;\nimport static android.view.ViewGroup.LayoutParams.MATCH_PARENT;\nimport static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;')
p.write_text(s)
