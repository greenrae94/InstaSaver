# InstaSaver keeps almost nothing by hand: OkHttp, WorkManager, Compose and
# kotlinx.serialization all ship their own consumer rules, and this app has no
# reflection of its own. The one exception is the Worker, which WorkManager
# instantiates by class name at runtime — R8 cannot see that reference.
-keep class com.raegreen.instasaver.DownloadWorker { <init>(...); }
