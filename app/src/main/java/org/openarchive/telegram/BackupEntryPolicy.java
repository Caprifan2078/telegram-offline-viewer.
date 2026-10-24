package org.openarchive.telegram;

import java.io.IOException;

/** Small, dependency-free validation of archive entry paths (including directory entries). */
final class BackupEntryPolicy {
    static final int MAX_ENTRIES=260_000;
    private BackupEntryPolicy(){}
    static void requireSafe(String name, boolean directory)throws IOException {
        if(name==null || name.isEmpty() || name.length()>1500 ||
           name.startsWith("/") || name.indexOf('\\')>=0 ||
           name.indexOf('\0')>=0 || name.indexOf(':')>=0)
            throw new IOException("Опасный путь в ZIP");
        if(directory != name.endsWith("/"))
            throw new IOException("Некорректный тип записи ZIP");
        String[] parts=name.split("/",-1);
        for(int i=0;i<parts.length;i++){
            String part=parts[i];
            if(part.equals(".") || part.equals("..") ||
               (part.isEmpty() && i!=parts.length-1))
                throw new IOException("Опасный компонент пути ZIP");
        }
    }
    static void requireFullBackupPath(String name,boolean directory)throws IOException {
        requireSafe(name,directory);
        if(directory){
            if(!(name.equals("media/") || name.equals("custom/") ||
                 name.matches("media/chat-[0-9]{1,4}/(?:[^/]+/)*")))
                throw new IOException("Неожиданный каталог ZIP");
        }else if(!name.equals("metadata.zip") && !name.equals("full-manifest.json") &&
                  !name.matches("media/chat-[0-9]{1,4}/.+") &&
                  !name.matches("custom/[a-zA-Z0-9_-]{1,96}\\.jpg")){
            throw new IOException("Недопустимый элемент резервной копии");
        }
    }
}
