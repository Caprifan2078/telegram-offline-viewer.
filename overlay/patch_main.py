from pathlib import Path
p=Path("app/src/main/java/org/offline/tgviewer/MainActivity.java")
s=p.read_text()
s=s.replace("import android.content.Intent;", "import android.content.Intent;\nimport android.content.ClipData;\nimport android.content.ActivityNotFoundException;")
s=s.replace("import android.widget.MediaController;\n","").replace("import android.widget.VideoView;\n","")
s=s.replace("private VideoView media;", "private SafeMediaView media;")
s=s.replace("media = new VideoView(this);", "media = new SafeMediaView(this);")
s=s.replace("        media.setBackgroundColor(0xff0b1320);\n", "")
begin=s.index("                    MediaController controls = ")
end=s.index("\n                });",begin)
s=s[:begin]+"""                    media.play(contentUri, this::hideMedia,
                            () -> confirmExternalPlayer(contentUri, ExportTree.mime(item.name)));"""+s[end:]
insert="""    private void confirmExternalPlayer(Uri contentUri, String mime) {
        new AlertDialog.Builder(this)
            .setTitle("Открыть в другом плеере?")
            .setMessage("Сторонний плеер получит доступ к этому видео и может иметь интернет. Используй только доверенное приложение.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Продолжить", (dialog, which) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(contentUri, mime);
                    intent.setClipData(ClipData.newUri(getContentResolver(), "Telegram media", contentUri));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Видеоплеер"));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, "Другой видеоплеер не установлен", Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    @Override protected void onPause() {
        if (media != null) media.pausePlayback();
        super.onPause();
    }



"""
s=s.replace("    private void hideMedia() {",insert+"    private void hideMedia() {")
assert "MediaController" not in s
p.write_text(s)
p=Path("app/build.gradle")
s=p.read_text().replace("versionCode 3","versionCode 4").replace("versionName '0.3.0'","versionName '0.4.0'")
p.write_text(s)
