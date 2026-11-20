WinLauncher ROOT — мастер-план лаунчера-копии Windows 11 для Samsung SM-T555 (с root)
Исполнитель: DeepSeek V4.1 Flash. Документ самодостаточный. Устройство: Galaxy Tab A 9.7 SM-T555, Android 7.1.1 (API 25), 1024×768, density 160 (1dp = 1px), 2 ГБ RAM, стоковая Samsung-прошивка, есть root (su), физическая кнопка Home.
Принципиальное отличие от безрутового плана: теперь у нас есть taskId, точные bounds, z-порядок, resize/move/close любых окон и инъекция ввода. Поэтому архитектура другая: ВСЕ окна (сторонние и встроенные) — настоящие freeform-задачи Android, а лаунчер — это единый менеджер задач + собственный «заголовок окна» (chrome) в стиле Win11 + панель задач/Пуск/оверлеи.
0. Правила для исполнителя (читать перед КАЖДЫМ этапом)
Делай ТОЛЬКО один этап (R0, R1, …) за раз. Не начинай следующий, пока не выполнена «Приёмка» текущего.
Ничего не придумывай. Числа, цвета, тайминги, имена классов, форматы протокола — только из этого файла. Если чего-то нет — выбери самое простое решение, запиши в DECISIONS.md одной строкой, иди дальше. Функционал не расширять.
Без сторонних библиотек (только Android SDK, Kotlin stdlib, org.json). Без Compose, корутин, DI. Потоки — Handler/Looper/Executor.
Скрытые API — только через reflection в try/catch (Throwable) с фолбэком. Тип оверлеев — только 2003 (TYPE_SYSTEM_ALERT).
Любая root-операция — только через RootShell или RootHelper (раздел 4). Прямые Runtime.exec("su") в других местах запрещены.
Все числа/цвета/тайминги — только в Tokens.kt (раздел 19). Все строки UI — strings.xml (ru). Магические числа запрещены.
UI-поток не блокировать: root-вызовы — только асинхронно (callback на main).
Не логировать содержимое чужих окон/уведомлений.
Параметры, зависящие от прошивки, лежат в Cal.kt (раздел 5) с значениями по умолчанию из плана. Не хардкодить их в логике.
После каждого этапа — отчёт: Сделано: (файлы) · Как проверить: (шаги на планшете) · Известные проблемы: · Отклонения от плана: (или «нет»).
Сборка: ./gradlew assembleDebug без ошибок.
Каждый этап обязан иметь рабочий откат: экран «Аварийное восстановление» (раздел 4.6) доступен с R0 и не ломается дальнейшими этапами.
1. Что даёт root и что остаётся невозможным
Возможность	Реализация
taskId, пакет, bounds, порядок z всех задач	getAllStackInfos() через RootHelper
Точное перемещение/ресайз любого окна	resizeTask(taskId, bounds, mode)
Поднять любое окно	ActivityManager.moveTaskToFront (REORDER_TASKS)
Закрыть окно	removeTask(taskId)
Настоящее «свёрнуто»	парковка задачи за экраном/под панелью (стратегии S1–S3, раздел 8.5)
Несколько окон одного приложения	разные taskId, группировка в панели как в Win11
Принудительно сделать fullscreen-приложение оконным	am stack movetask <id> 2 true + resize
Своя шапка окон Win11 поверх Samsung-caption	ChromeView (оверлей 2003), раздел 7
Виртуальные рабочие столы	парковка задач по desktopId, раздел 9
Инъекция клавиш/тапов	InjectInputEvent в RootHelper
Автовыдача разрешений (a11y, overlay, usage, notif, write settings)	AutoGrant, раздел 4.5
Скрыть системный статус-бар	policy_control immersive.status=*
Wi-Fi, Bluetooth, моб. данные, авиарежим, яркость, питание, перезагрузка	svc, settings, reboot
Реальные скриншоты/миниатюры без MediaProjection	screencap
Реальный диспетчер задач (CPU/RAM/kill)	ps, /proc, dumpsys meminfo, am force-stop
Смена DPI с откатом	wm density + таймер отката
Что НЕ делаем / невозможно:
Мягкий blur (Mica/Acrylic): заменяем полупрозрачным цветом.
Скруглённые углы у чужих окон: недоступно (углы только у своих оверлеев).
Перехват аппаратной кнопки Recents: не обрабатываем.
Win+, (peek), второй монитор, патчинг SystemUI/Xposed: не делаем.
2. Конфигурация проекта
Package ru.winlauncher, Kotlin 1.8+, AGP 7.4+, minSdk 25, targetSdk 25, compileSdk 33.
Ориентация: sensorLandscape (портрет не поддерживается; при портрете оверлеи скрыть).
Компоненты:
MainActivity — HOME, singleTask (рабочий стол: обои, иконки).
DockService — START_STICKY, держит все оверлеи, TaskRepo, RootHelper.
WindowWatcherService : AccessibilityService — только горячие клавиши и события (не основа).
NotifyService : NotificationListenerService.
BootReceiver, RecoverReceiver (action ru.winlauncher.RECOVER).
Встроенные приложения — отдельные Activity (раздел 17), каждая с resizeableActivity=true, taskAffinity="ru.winlauncher.app.<name>", documentLaunchMode="always", launchMode="standard", configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|density".
Разрешения манифеста: SYSTEM_ALERT_WINDOW, REORDER_TASKS, KILL_BACKGROUND_PROCESSES, EXPAND_STATUS_BAR, RECEIVE_BOOT_COMPLETED, WRITE_SETTINGS, CHANGE_WIFI_STATE, ACCESS_WIFI_STATE, BLUETOOTH, BLUETOOTH_ADMIN, CAMERA, READ/WRITE_EXTERNAL_STORAGE, PACKAGE_USAGE_STATS, ACCESS_NOTIFICATION_POLICY, QUERY_ALL_PACKAGES.
Хранилище: SharedPreferences("winlauncher") + JSON.
3. Архитектура
MainActivity (HOME, рабочий стол)
DockService (процесс приложения)
 ├─ RootShell      — один постоянный `su`-shell для разовых команд
 ├─ RootHelper     — клиент к root-процессу HelperMain (быстрые вызовы)
 ├─ TaskRepo       — модель всех задач (taskId → TaskModel), z-порядок, MRU
 ├─ WindowManagerX — все действия над окнами (move/resize/max/snap/min/close/focus)
 ├─ ChromeManager  — ChromeView на каждое видимое окно
 ├─ DesktopsManager— виртуальные столы
 ├─ OverlayHost    — добавление/удаление оверлеев 2003
 ├─ TaskbarOverlay, StartOverlay, QuickOverlay, NotifOverlay,
 │  SwitcherOverlay, TaskViewOverlay, SnapPreviewOverlay, SnapFlyoutOverlay,
 │  TooltipOverlay, ContextMenuOverlay, ThumbCache
 └─ Hotkeys (из WindowWatcherService.onKeyEvent)

HelperMain (процесс root, app_process, uid 0)
 ├─ stdin/stdout JSON-lines протокол (раздел 4.3)
 ├─ ActivityManager hidden API: getAllStackInfos / resizeTask / removeTask
 ├─ InputManager.injectInputEvent
 └─ screencap
Потоки: main (UI), root-io (один поток на RootShell), helper-io (чтение stdout helper), work (Executor на 2 потока для парсинга/миниатюр). Все коллбэки в UI — через Handler(Looper.getMainLooper()).
4. Root-слой
4.1. RootShell (разовые команды)
Один процесс su, постоянно открытый. Команда отправляется строкой <cmd> 2>&1; echo __WLEND__<id>:$?. Чтение до маркера. Таймаут по умолчанию 4000 мс; при таймауте — destroy() процесса и перезапуск, команда возвращает ошибку. Очередь: строго последовательная, один поток.
object RootShell {
  fun start(): Boolean            // exec("su"), проверка `id` → "uid=0"
  fun run(cmd: String, timeoutMs: Long = 4000, cb: (Result)->Unit)  // async
  fun runSync(cmd: String, timeoutMs: Long = 4000): Result          // ТОЛЬКО не из UI-потока
  data class Result(val ok: Boolean, val code: Int, val out: String)
}
Использование: settings, svc, wm, am start, pm, appops, reboot, ps, dumpsys. НЕ использовать для частых операций (каждый am/input стартует JVM ≈ 250–500 мс на этом планшете).
4.2. RootHelper (быстрые вызовы, ядро производительности)
Запуск (отдельный процесс su, связь через его stdin/stdout — без сокетов, чтобы не упираться в SELinux):
su -c "CLASSPATH=$(pm path ru.winlauncher | cut -d: -f2) exec app_process /system/bin ru.winlauncher.root.HelperMain"
HelperMain — класс только с framework-API и org.json, без Context. Клиент RootHelper в приложении: call(op, args, cb), перезапуск при смерти (экспоненциально 0.5/1/2/5 с, максимум 5 попыток, затем баннер «Root-сервис недоступен» и Cal.degraded=true).
4.3. Протокол (по одной JSON-строке)
Запрос: {"id":N,"op":"...","a":{...}} → ответ: {"id":N,"ok":true,"d":{...}} или {"id":N,"ok":false,"e":"текст"}. Событие от helper: {"evt":"stacks","d":[...]}.
op	args	действие
ping	—	{"uid":0}
stacks	—	список стеков (см. 5.2)
resize	tid,l,t,r,b,mode	IActivityManager.resizeTask(tid, Rect, mode)
remove	tid	IActivityManager.removeTask(tid)
front	tid	moveTaskToFront(tid, 0, null) (фолбэк: shell am stack movetask)
tostack	tid,stack,top	IActivityManager.moveTaskToStack (фолбэк: shell am stack movetask)
key	code,meta,down,up	InputManager.injectInputEvent (KeyEvent)
tap	x,y	DOWN+UP MotionEvent, source touchscreen
swipe	x1,y1,x2,y2,ms	серия MotionEvent
shell	cmd,ms	выполнить sh -c в root, вернуть stdout
cap	path	screencap > path (raw), chmod 666 path
forcestop	pkg	am force-stop pkg через shell
Событие stacks helper присылает сам, опрашивая каждые 200 мс (только если экран включён: проверять dumpsys-флаг не нужно, помогает сообщение screen от приложения {"op":"screen","a":{"on":true}}), только при изменении (сравнение по строке).
4.4. Эталонный код helper (использовать как основу)
val iam = Class.forName("android.app.IActivityManager")
val amn = Class.forName("android.app.ActivityManagerNative")
val am = amn.getMethod("getDefault").invoke(null)

fun stacks(): List<StackSnap> {
  val list = iam.getMethod("getAllStackInfos").invoke(am) as List<*>
  return list.map { s ->
    val c = s!!.javaClass
    val stackId = c.getField("stackId").getInt(s)
    val bounds = c.getField("bounds").get(s) as Rect
    val tids = c.getField("taskIds").get(s) as IntArray
    val names = c.getField("taskNames").get(s) as Array<String>
    val tb = c.getField("taskBounds").get(s) as Array<Rect>
    val visible = c.getField("visible").getBoolean(s)
    StackSnap(stackId, bounds, tids, names, tb, visible)
  }
}
fun resize(tid:Int, r:Rect, mode:Int) =
  iam.getMethod("resizeTask", Int::class.java, Rect::class.java, Int::class.java).invoke(am, tid, r, mode)
fun remove(tid:Int) = iam.getMethod("removeTask", Int::class.java).invoke(am, tid)
Инъекция:
val im = Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null)
val inj = im.javaClass.getMethod("injectInputEvent", android.view.InputEvent::class.java, Int::class.java)
inj.invoke(im, event, 0) // INJECT_EVENTS_MODE_ASYNC
Все вызовы helper обёрнуты в try/catch; ошибка → ok:false,e:e.toString(). Если getAllStackInfos недоступен, helper переходит на разбор am stack list (формат строк:
Stack id=2 bounds=[l,t][r,b] ... и taskId=N: pkg/cls bounds=[l,t][r,b] userId=0 visible=true topActivity=...).
4.5. AutoGrant (root выдаёт всё сам)
Выполняется при первом запуске и при каждом старте DockService, если чего-то не хватает (через RootShell):
appops set ru.winlauncher SYSTEM_ALERT_WINDOW allow
pm grant ru.winlauncher android.permission.READ_EXTERNAL_STORAGE
pm grant ru.winlauncher android.permission.WRITE_EXTERNAL_STORAGE
pm grant ru.winlauncher android.permission.CAMERA
pm grant ru.winlauncher android.permission.WRITE_SECURE_SETTINGS
appops set ru.winlauncher GET_USAGE_STATS allow
settings put system ... (WRITE_SETTINGS) → appops set ru.winlauncher WRITE_SETTINGS allow
settings put secure enabled_accessibility_services ru.winlauncher/ru.winlauncher.service.WindowWatcherService   (с сохранением уже включённых через ':')
settings put secure accessibility_enabled 1
settings put secure enabled_notification_listeners ru.winlauncher/ru.winlauncher.service.NotifyService  (с сохранением)
settings put global force_resizable_activities 1
dumpsys deviceidle whitelist +ru.winlauncher
Домашний экран: cmd package set-home-activity ru.winlauncher/.MainActivity (если команда не существует — pm set-home-activity; если и её нет — показать системный выбор «Всегда» один раз).
Статус-бар: если в Параметрах включено «Скрыть статус-бар» (по умолчанию ВКЛ) — settings put global policy_control immersive.status=*.
4.6. Аварийное восстановление (обязательно с R0)
Экран RecoveryActivity (в лаунчере: Параметры → «Аварийное восстановление», а также запуск adb shell am start -n ru.winlauncher/.RecoveryActivity).
Действия (через RootShell): settings delete global policy_control, wm density reset, wm size reset, вернуть все parked-задачи в видимую область (resize), убить HelperMain (pkill -f HelperMain), отключить оверлеи (stopService), сбросить Prefs.
RecoverReceiver делает то же самое по adb shell am broadcast -a ru.winlauncher.RECOVER.
Crash guard: если DockService падал ≥3 раза за 2 минуты — при следующем запуске автоматически включается «безопасный режим»: нет ChromeView, нет policy_control, нет парковки задач; показывается баннер.
5. Калибровка платформы (этап R0) и модель задач
Прошивка Samsung отличается от AOSP. Поэтому R0 создаёт экран RootLabActivity, который прогоняет тесты, показывает результат и пишет /sdcard/WinLauncher/calibration.json. Код читает значения из Cal.kt (объект с полями ниже; значения подтягиваются из JSON если файл есть, иначе значения по умолчанию из таблицы).
Поле Cal	Что проверяет тест	По умолчанию
suOk	su -c id → uid=0	— (блокирующая ошибка)
freeformStackId	stack, куда попадает окно после freeform-запуска (Спр. §5.1)	2
fullscreenStackId	stack обычных приложений	1
stacksTopFirst	порядок стеков/задач в getAllStackInfos сверху вниз? (тест: открыть 3 приложения A,B,C, C сверху)	true
tasksTopFirst	порядок taskIds внутри стека сверху вниз	true
resizeMode	какой mode (1, 0, 3) реально двигает окно	1
moveResizeWorks	resize меняет положение/размер без перезапуска	true
parkStrategy	S1 «за экран вправо», S2 «под панель», S3 «overlay» (какая первая сработала, раздел 8.5)	S1
parkOffX	смещение X для S1	2000
captionH	высота Samsung-caption (из a11y-узла caption либо bounds.top разницы)	32
captionAlways	показывает ли Samsung caption у неактивных окон	true
multiInstance	FLAG_ACTIVITY_MULTIPLE_TASK даёт второй taskId у пакета X	true
forceFreeformMove	moveTaskToStack(tid,2,true) + resize переводит fullscreen-задачу в окно	true
statusBarHide	policy_control immersive.status=* скрывает статус-бар	true
homeKeyKeepsFreeform	после Home freeform-окна остаются видимы	true
capMs	время screencap raw (мс)	400
setHomeCmd	cmd package set-home-activity существует	true
Правило: если тест не пройден — выбрать фолбэк из раздела 8.5 или 7.6 и записать в DECISIONS.md. Функционал никогда не должен падать из-за непроверенного допущения.
5.2. TaskModel и TaskRepo
data class TaskModel(
  val taskId: Int, val pkg: String, val component: String,
  var bounds: Rect, var stackId: Int,
  var z: Int,                 // 0 = самый верхний
  var state: WinState,        // NORMAL, MAXIMIZED, SNAP_L, SNAP_R, SNAP_TL, SNAP_TR, SNAP_BL, SNAP_BR, FULLSCREEN, MINIMIZED
  var restoreRect: Rect?,     // bounds до MAX/SNAP/MINIMIZE
  var parked: ParkReason?,    // null | MINIMIZE | DESKTOP
  var desktopId: Int,         // 1..4
  var lastFocusAt: Long,
  var label: String, var isOwn: Boolean )
TaskRepo:
подписан на событие stacks от helper; строит список TaskModel для задач стеков freeformStackId и fullscreenStackId (HOME-стек и задачи ru.winlauncher/.MainActivity игнорируются; системные пакеты com.android.systemui, com.samsung.android.*overlay игнорируются);
сохраняет restoreRect/state/desktopId/parked между обновлениями (ключ taskId);
новую задачу получает с state=NORMAL, desktopId=текущий, определяет NORMAL/MAXIMIZED/FULLSCREEN по bounds (допуск 4 px: ==WORK → MAXIMIZED, ==DISPLAY → FULLSCREEN);
исчезнувшие задачи удаляет (окно закрыто);
вычисляет z: индекс в общем порядке «сверху вниз» с учётом stacksTopFirst/tasksTopFirst; активное окно = верхнее среди не-parked и не-HOME;
ведёт MRU List<Int> (taskId по убыванию lastFocusAt), обновляется при смене активного;
уведомляет слушателей только при изменении (сравнивать поля).
Размеры рабочей области: DISPLAY=(0,0,W,H), WORK=(0,0,W,H−TASKBAR_H) (статус-бар скрыт). Если statusBarHide=false → WORK.top = 24.
6. Единая модель окон
Любое окно = задача Android. Сторонние приложения и встроенные (Проводник, Блокнот…) управляются одним кодом по taskId.
Рабочий стол (MainActivity) — всегда нижний слой, окна всегда над ним, наши оверлеи 2003 — над окнами.
Запуск окна (WindowManagerX.launch(pkgOrComponent, bounds?)):
Intent = launch intent пакета (для встроенных — явный компонент) + FLAG_ACTIVITY_NEW_TASK;
для нового окна — добавить FLAG_ACTIVITY_MULTIPLE_TASK только если Cal.multiInstance и пользователь явно просит «новое окно» (middle-click по кнопке, пункт Jump List «Новое окно»); обычный запуск уже запущенного приложения = поднять существующее окно (раздел 10.3);
ActivityOptions: setLaunchStackId(Cal.freeformStackId) (reflection) → фолбэк setLaunchWindowingMode(5); setLaunchBounds(initialRect);
начальный Rect: размер по умолчанию окна (по приложению; для сторонних 800×560), позиция = каскад: (96 + 32·n, 48 + 32·n) где n = число окон текущего стола mod 8; нижний край ≤ WORK.bottom.
через 800 мс после старта TaskRepo должен показать новую задачу; если она в fullscreenStackId и Prefs.forceFreeform=true (по умолчанию) и Cal.forceFreeformMove → tostack(tid,freeformStackId,true) затем resize(initialRect). Если приложение отказалось — окно считается FULLSCREEN-задачей, панель задач при этом остаётся (раздел 10.7).
Фокус: front(taskId) (ActivityManager.moveTaskToFront), затем lastFocusAt=now.
7. Chrome — шапка окон Win11 поверх Samsung-caption
7.1. Идея
Для каждой видимой не-fullscreen задачи в freeform рисуем свой оверлей ChromeView, который полностью перекрывает Samsung-caption (его высота Cal.captionH). Заголовок рисуем мы: иконка, название, три кнопки Win11. Перетаскивание и кнопки обрабатываем сами через resize/remove. Нативные края для ресайза Samsung не трогаем.
7.2. Геометрия
captionRect = Rect(task.l, task.t, task.r, task.t + Cal.captionH).
Видимая часть: из captionRect вычитаются прямоугольники всех более верхних видимых окон (z меньше) и всех окон, у которых parked != null не вычитаются. Из получившихся прямоугольников выбирается наибольший по площади → visRect. Если visRect.width < 48 — Chrome для этой задачи не показываем (виден нативный caption).
fun subtract(a: Rect, b: Rect): List<Rect> { // a минус b
  if (!Rect.intersects(a, b)) return listOf(a)
  val r = mutableListOf<Rect>()
  if (b.top > a.top) r += Rect(a.left, a.top, a.right, b.top)
  if (b.bottom < a.bottom) r += Rect(a.left, maxOf(a.top,b.top), a.right, b.bottom.coerceAtLeast(maxOf(a.top,b.top)))  // средняя полоса
  /* реализовать полностью: верх, низ, лево и право средней полосы */
  return r.filter { it.width() > 0 && it.height() > 0 }
}
(Исполнитель реализует Rects.subtract полностью и покрывает юнит-тестом 5 случаев.)
Окно-оверлей ставится ровно в visRect; рисование внутри смещено так, чтобы шапка отображалась как будто окно = captionRect (translate на captionRect.left − visRect.left).
7.3. Флаги и жизненный цикл
Тип 2003, флаги NOT_FOCUSABLE | NOT_TOUCH_MODAL | LAYOUT_IN_SCREEN | LAYOUT_NO_LIMITS.
Максимум одновременных ChromeView: 8 (самые верхние по z на текущем столе). Остальные — нативный caption.
Обновление позиции — при изменении bounds/z (по событию TaskRepo), не по таймеру. Во время drag своей шапки позиция обновляется синхронно с resize.
Скрывать: IME-панель выше, шторка, keyguard, поворот (ROTATION_HIDE), задача FULLSCREEN, задача парковки, открыт TaskView, Switcher.
Пул view: переиспользовать, не создавать заново на каждый кадр.
7.4. Внешний вид шапки
Высота Cal.captionH (рисовать кнопки высотой = captionH, ширина 46).
Фон: активное окно BG_TITLE_ACTIVE, неактивное BG_TITLE_INACTIVE; светлая тема аналогично.
Слева: иконка 16×16 (отступ 12), название 12 sp (отступ 8), обрезка «…» перед кнопками.
Справа: «—», «□» (или «❐» при MAXIMIZED), «×». Hover: фон HOVER; у «×»: CLOSE_HOVER, иконка белая; pressed: PRESSED/CLOSE_PRESSED.
Нижняя линия 1 px BORDER.
7.5. Взаимодействие с шапкой
Действие	Результат
тап по телу окна/шапке неактивного	front(taskId) (поднять, активировать)
drag по шапке	перемещение: на каждом ACTION_MOVE (не чаще 1 раз в 16 мс, пропуская кадры пока предыдущий resize не завершён) resize(tid, newRect, mode)
старт drag из MAXIMIZED/SNAP	сразу восстановить размер restoreRect с центрированием шапки под курсором, затем продолжать
drag: курсор у верха (y ≤ 8)	превью MAXIMIZED; у лев/прав края (x ≤ 8 / x ≥ W−8) — превью половины; в углу (квадрат 48) — четверть
отпускание с превью	применить snap/max (раздел 8.3)
двойной тап/клик по шапке (≤ 300 мс)	MAX ↔️ NORMAL
ПКМ / долгий тап по шапке	системное меню окна: Восстановить, Переместить, Размер, Свернуть, Развернуть, Закрыть (Alt+F4)
hover над «□» >500 мс или долгий тап	SnapFlyoutOverlay (8.4)
«—»	свернуть (8.5)
«×»	закрыть: remove(tid)
средняя кнопка по шапке	закрыть
7.6. Фолбэк
Если Cal.moveResizeWorks=false или helper недоступен: Chrome не показываем; окна работают с нативным Samsung-caption, а Max/Snap/Close делаются тапами по узлам com.samsung.android.framework.res:id/{minimize_window,maximize_window,close_window} через dispatchGesture (способ из безрутового плана) — только для этих случаев.
8. Поведение окон
8.1. Состояния и переходы (одинаково для всех задач)
Из	Действие	В
NORMAL	□ / двойной клик / Win+↑ / drag к верху	MAXIMIZED (restoreRect=bounds)
MAXIMIZED	❐ / двойной клик / Win+↓ / drag вниз	NORMAL (restoreRect)
NORMAL	drag к краю / Win+←/→	SNAP_L / SNAP_R
SNAP_L	Win+→	NORMAL; Win+↑ → SNAP_TL; Win+↓ → SNAP_BL
SNAP_R	Win+←	NORMAL; Win+↑ → SNAP_TR; Win+↓ → SNAP_BR
NORMAL	drag в угол	SNAP_TL/TR/BL/BR
MAXIMIZED	Win+↓	NORMAL; ещё раз Win+↓ → MINIMIZED
любое видимое	«—» / Win+↓ (из NORMAL) / клик по активной кнопке панели	MINIMIZED
MINIMIZED	клик по кнопке панели / Alt+Tab / Task View	состояние до сворачивания (restoreRect)
любое	F11	FULLSCREEN (bounds=DISPLAY, панель скрыта) ↔️ прежнее
любое	×, Alt+F4	задача удалена
8.2. Целевые Rect
Пусть W,H, WORK=(0,0,W,H−TASKBAR_H), cx=W/2, cy=WORK.bottom/2.
MAXIMIZED = WORK; FULLSCREEN = DISPLAY
SNAP_L=(0,0,cx,WORK.bottom); SNAP_R=(cx,0,W,WORK.bottom)
SNAP_TL=(0,0,cx,cy); SNAP_TR=(cx,0,W,cy); SNAP_BL=(0,cy,cx,WORK.bottom); SNAP_BR=(cx,cy,W,WORK.bottom)
Минимальный размер окна для ресайза: WINDOW_MIN_W × WINDOW_MIN_H.
Окно в NORMAL не может выйти за экран так, чтобы шапка была вне экрана: top ≥ 0, видимая часть шапки ≥ 48 px по горизонтали и целиком по вертикали.
8.3. Применение с анимацией
WindowManagerX.animateTo(tid, from, to, ms): ANIM_STEPS=5 шагов линейной интерполяции с ease-out; следующий resize отправляется только после ответа предыдущего; если шаг занял >60 мс — прыгнуть сразу в to. После последнего шага — финальный resize(to), обновление state/restoreRect. Для мгновенных операций (drag) анимации нет.
8.4. Snap Layouts и Snap Assist
SnapFlyoutOverlay: карточка 4 макета (50/50, 66/33, 3 колонки, 4 четверти) над кнопкой «□»; зоны подсвечиваются при hover; клик применяет Rect макета (по WORK). После snap одного окна, если на столе есть другие видимые окна, показывается Snap Assist: миниатюры оставшихся окон (ThumbCache) на свободной области; клик → окно в свободную зону, Esc — закрыть.
8.5. Сворачивание (парковка) — стратегии
ParkStrategy выбирается по Cal.parkStrategy, при неудаче проверка → следующая:
S1: resize(tid, Rect(W+Cal.parkOffX, t, W+Cal.parkOffX+w, t+h)) — окно за правой границей. Проверка успеха: через 300 мс bounds в TaskRepo ≈ запрошенному (допуск 4 px).
S2: resize(tid, Rect(0, H−40, 240, H)) — окно размером 240×40 под панелью задач (панель выше, его не видно).
S3: свой DesktopOverlay поверх окна (как «чистый рабочий стол»), окна остаются на местах.
Перед парковкой: restoreRect=bounds (если не MAX/SNAP — иначе берётся прежний restoreRect), parked=MINIMIZE. После парковки активным становится следующее по MRU непаркованное окно текущего стола (front), либо никто (виден рабочий стол).
Восстановление: resize(restoreRect) + front(tid); parked=null.
Анимация сворачивания: если ThumbCache содержит миниатюру окна моложе 5 с — «призрак» (ImageView-оверлей) летит к кнопке панели 200 мс (ANIM_MINIMIZE), затем парковка; иначе без анимации.
8.6. Закрытие
remove(tid); если ответ ok:false — фолбэк: тап по close_window через a11y; если и он недоступен — am force-stop pkg только по явному «Снять задачу» в Диспетчере задач. Для встроенного Блокнота с несохранённым текстом — диалог внутри самой Activity.
8.7. Прочее
Always-on-top: не реализуем.
Новое окно появляется активным.
Окна текущего стола, не-parked — «видимые».
Resize по краям — нативный Samsung (края окна). Свои 8-пиксельные ручки — только в R13 (опционально).
9. Виртуальные рабочие столы (R13, опционально, но спроектировано)
4 стола максимум; текущий Prefs.desktop (1..4).
Переключение (Win+Ctrl+←/→, Task View → панель столов): задачи других столов получают parked=DESKTOP и уходят в S1/S2 (как при сворачивании), задачи нового стола с parked=DESKTOP возвращаются в restoreRect; MINIMIZE-парковка сохраняется.
Win+Ctrl+D — новый стол, Win+Ctrl+F4 — закрыть текущий (его окна переезжают на соседний).
Новое окно получает desktopId текущего стола.
Панель задач показывает только окна текущего стола.
Task View: сверху строка миниатюр столов (по одной миниатюре обоев), кнопка «+ Создать».
10. Панель задач
10.1. Вид и размеры
Оверлей 2003 (NOT_FOCUSABLE | NOT_TOUCH_MODAL | LAYOUT_IN_SCREEN), высота 48, ширина экран, низ экрана. Фон BG_TASKBAR, верхняя линия 1 px BORDER.
Центр (по центру экрана): [Пуск][Поиск][Task View][Виджеты] | закреплённые | запущенные. Справа: трей (Wi-Fi, громкость, батарея), время HH:mm и дата dd.MM.yyyy (две строки), колокольчик, «Показать рабочий стол» 6 px.
Опция «Выравнивание: слева» (Параметры).
10.2. Кнопка приложения
44×40, радиус 4, иконка 24. Индикатор под иконкой: не запущено — нет; запущено неактивно — 6×3 FG_SECONDARY; активно — 16×3 ACCENT; свёрнуто — 6×3, иконка alpha 0.7. Группа с несколькими окнами — «стопка»: справа/слева тонкая вторая рамка (смещение 3 px). Бейдж уведомлений (кружок 8 px) в правом верхнем углу (после R12).
10.3. Поведение клика (как в Win11)
Состояние группы	ЛКМ	ПКМ / долгий тап	Средняя
не запущено (закреплено)	запустить	Jump List	запустить
1 окно, не активно	front (если MINIMIZED — восстановить)	Jump List	новое окно
1 окно, активно	свернуть	Jump List	новое окно
>1 окон	показать ряд превью над кнопкой; клик по превью — выбрать окно; повторный ЛКМ по кнопке — следующее окно по MRU	Jump List	новое окно
Hover по кнопке с окнами (мышь, >TOOLTIP_DELAY) — всплывающие превью (ThumbCache), на каждом × для закрытия.			
10.4. Jump List
Пункты: название приложения (открыть/новое окно), «Закрепить на панели задач»/«Открепить», «Закрыть окно» (или «Закрыть все окна»). Меню над кнопкой.
10.5. Кнопки группы слева
Пуск → Start; Поиск → Start с фокусом на поиске; Task View → TaskViewOverlay; Виджеты → панель слева 360×500 (часы, календарь, батарея, ничего больше).
10.6. Трей
Часы тикают по ACTION_TIME_TICK. Клик по времени/дате/колокольчику → NotifOverlay; клик по Wi-Fi/громкости/батарее → QuickOverlay; ПКМ по часам — меню «Настроить дату/время» (открывает системные настройки).
10.7. Видимость
Панель всегда над окнами. Скрывается: у FULLSCREEN-задачи (F11/видео), шторка, keyguard, поворот. IME перекрывает панель — это нормально. Если приложение осталось fullscreen (не удалось перевести в окно) — панель остаётся видимой и WORK ему не навязывается.
11. Меню Пуск
Оверлей 2003, фокусируемый (NOT_TOUCH_MODAL | WATCH_OUTSIDE_TOUCH), по центру по горизонтали, низ = H − TASKBAR_H − START_BOTTOM_MARGIN, размер 560×620 (над IME подтянуть: высота = visibleFrame.bottom − top − 8). Анимация ANIM_START_OPEN.
Содержимое сверху вниз:
Поле поиска (высота 40, радиус 20): ввод мгновенно фильтрует приложения (подстрока, без регистра), встроенные приложения, файлы /sdcard по имени (до 5), веб-поиск последней строкой («Искать в интернете»). Enter запускает первый результат.
«Закреплённые»: сетка 6 колонок, ячейка 72×84 (иконка 32 + подпись 12 sp, 2 строки). Drag — переупорядочить. Кнопка «Все приложения ›».
«Рекомендуем»: 6 последних запущенных (из UsageStats, иначе из собственной истории).
Футер: аватар + «Пользователь»; кнопка питания → меню: «Заблокировать» (input keyevent 26 через helper key), «Сон» (то же), «Перезагрузить» (reboot), «Завершение работы» (reboot -p), с подтверждением.
Режим «Все приложения»: алфавитный список с буквенными заголовками, быстрая навигация по буквам.
ПКМ по элементу: «Закрепить на Пуске», «Закрепить на панели задач», «Открыть расположение»(Проводник), «Удалить» (pm uninstall через RootShell с подтверждением), «Сведения».
Закрытие: Win, Esc, клик вне, запуск приложения.
12. Alt+Tab, Task View, миниатюры
12.1. ThumbCache
Реальные миниатюры без MediaProjection: cap(path) → raw RGBA (заголовок 12 байт: w,h,format LE) → Bitmap → crop(task.bounds) → scale до 320×240 → LRU-кэш (≤ 24 штуки, ≤ 12 МБ). Съёмка: (а) в момент, когда окно теряет фокус (асинхронно, сразу после front на другое окно не раньше, а до вызова — т.е. снимать, пока окно ещё сверху); (б) по требованию при открытии Task View/Switcher для активного окна. Неактивные окна показывают последнюю миниатюру (если нет — иконка по центру на фоне BG_PANEL). Минимум 2 с между снимками. Файл cap.raw — в cacheDir приложения (root пишет, chmod 666).
12.2. Alt+Tab (SwitcherOverlay)
Оверлей 2003 NOT_FOCUSABLE. Показ: Alt зажат + Tab. По центру ряд карточек 160×120 (миниатюра + иконка 24 + название 12 sp, обрезка), выбранная — рамка ACCENT 2 px, радиус 8. Порядок MRU текущего стола (парковка MINIMIZE тоже включена). Tab/→ вперёд, Shift+Tab/← назад, Esc отмена, отпускание Alt — подтвердить (восстановить если свёрнуто + front). Клик по карточке — выбор. Быстрое нажатие Alt+Tab (без задержки) просто переключает на предыдущее окно, оверлей появляется только после 200 мс удержания.
12.3. Task View (Win+Tab / кнопка)
Затемнение #99000000. Сетка карточек 240×180 всех окон текущего стола (в центре, перенос строк). Каждая карточка: миниатюра, иконка+название, × в правом верхнем углу. Клик — активировать, Esc/клик вне — закрыть. Внизу строка столов (раздел 9); если R13 не реализован — одна плашка «Рабочий стол 1».
Во время Task View все ChromeView скрыты.
13. Быстрые настройки, уведомления, питание, экран
13.1. QuickOverlay (Win+A)
360×400, справа внизу над панелью (отступ 12), NOT_FOCUSABLE. Плитки 3×2: Wi-Fi, Bluetooth, Авиарежим, Мобильные данные, Не беспокоить, Фонарик, Автоповорот (всего 7 → сетка 4×2, последняя пустая). Ниже слайдеры: яркость, громкость. Внизу: батарея % слева, кнопка «Параметры» (открывает наши Параметры) справа.
Реализация через root:
Wi-Fi: svc wifi enable|disable; Bluetooth: BluetoothAdapter (фолбэк service call bluetooth_manager 6/8); мобильные данные: svc data enable|disable; авиарежим: settings put global airplane_mode_on 1 + am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true; DND: NotificationManager.setInterruptionFilter; фонарик: CameraManager.setTorchMode; яркость: settings put system screen_brightness N (0–255; screen_brightness_mode 0); громкость: AudioManager; автоповорот: settings put system accelerometer_rotation 0|1.
Состояния плиток читаются раз при открытии и по broadcast'ам.
13.2. NotifOverlay (Win+N)
Ширина 380 слева-направо справа: список уведомлений (NotifyService: иконка, заголовок, текст, время, × очистить, кнопки-действия через PendingIntent), ниже календарь месяца (7×6, сегодня ACCENT). Закрытие — клик вне/Esc.
13.3. Питание/экран/системное
Блокировка: helper key 26; Перезагрузка: reboot; Выключение: reboot -p.
Скриншот: PrtScn (KEYCODE_SYSRQ) или Win+Shift+S → cap + Bitmap → PNG в /sdcard/Pictures/Screenshots/Screenshot_yyyyMMdd_HHmmss.png; Win+Shift+S — полноэкранный оверлей выбора области (drag прямоугольника), сохранение области, тост «Сохранено».
Масштаб (DPI): Параметры → Экран: 100% (160), 125% (200), 150% (240). Применение wm density N; через 15 с автооткат wm density reset, если пользователь не нажал «Сохранить».
Статус-бар: переключатель «Скрыть системный статус-бар» (policy_control).
14. Рабочий стол (MainActivity)
Обои: градиент по умолчанию (#0F2A5C → #1E6FD9 + светлый блик), 4 встроенных, свой файл (SAF). Рисуются на MainActivity, поверх WallpaperManager не пишем.
Иконки: сетка 88×96, привязка к ячейкам, по умолчанию: «Этот компьютер»(Проводник), «Корзина»(Проводник/.Trash), «Параметры».
Выделение: клик (рамка ACCENT + заливка #334D60CDFF), drag на пустом месте — рамка выделения, двойной клик/Enter — открыть.
ПКМ по пустому: «Вид», «Сортировать», «Обновить», «Создать» (папка/текст), «Персонализация», «Параметры экрана». ПКМ по иконке: «Открыть», «Удалить с рабочего стола», «Свойства».
Нажатие аппаратной кнопки Home (повторный onNewIntent) = «Показать рабочий стол»: свернуть все окна текущего стола (если Cal.homeKeyKeepsFreeform=true); повторное нажатие — восстановить набор.
15. Горячие клавиши (WindowWatcherService.onKeyEvent + dispatchKeyEvent)
Поглощаем только перечисленные. Остальное — return false. Auto-repeat игнорировать (кроме стрелок).
Комбинация	Действие
Win	Пуск
Win+D	показать рабочий стол (свернуть все/восстановить набор)
Win+E	Проводник
Win+S / Win+Q	Пуск с поиском
Win+A	Быстрые настройки
Win+N	Уведомления
Win+Tab	Task View
Win+I	Параметры
Win+R	«Выполнить»
Win+X	меню быстрых ссылок (Диспетчер задач, Параметры, Проводник, Выполнить, Завершение работы)
Win+L	блокировка
Win+M	свернуть все
Win+Home	свернуть все, кроме активного
Win+↑/↓/←/→	MAX/восстановить-свернуть/snap L/snap R (раздел 8.1)
Win+1…9	N-я кнопка панели (запуск/активация)
Win+Ctrl+←/→/D/F4	рабочие столы (если R13)
Win+Shift+S	скриншот-область
PrtScn	скриншот экрана
Alt+Tab, Shift+Alt+Tab	переключатель
Alt+F4	закрыть активное окно (remove)
Alt+Space	системное меню активного окна
Ctrl+Shift+Esc	Диспетчер задач
F11	fullscreen активного окна
Esc	закрыть оверлей
Не перехватывать: Win+Space, Ctrl+C/V/X/Z, буквы, F-клавиши (кроме F11), поля паролей.	
16. Мышь, стилус, сенсор
Hover: state_hovered везде (подсветка HOVER), PointerIcon для краёв/ссылок.
ПКМ: BUTTON_SECONDARY (Samsung может прислать как ACTION_DOWN с buttonState) → контекстное меню. Средняя: закрыть окно по шапке / новое окно по кнопке панели.
Колесо: ACTION_SCROLL; Ctrl+колесо — масштаб иконок рабочего стола.
Тап = ЛКМ; долгий тап (500 мс) = ПКМ; двойной тап (≤ 300 мс) = двойной клик; второй палец в drag игнорировать.
Минимальная touch-цель 40 px (кнопки шапки 46×captionH — исключение).
Контекстное меню (ContextMenuOverlay): пункты 36 px, мин. ширина 200, радиус 8, иконка слева, горячая клавиша справа, разделители, подменю 1 уровня, стрелки/Enter, не выходит за экран.
17. Встроенные приложения (отдельные Activity-задачи)
Каждое открывается как обычная freeform-задача и управляется всем стеком из разделов 6–8.
Activity	Функции	Размер по умолчанию
Проводник	/sdcard, «хлебные крошки», назад/вперёд/вверх, сортировка (имя/дата/размер), ПКМ: Открыть/Копировать/Вырезать/Вставить/Переименовать/Удалить/Свойства, создание папки, «Быстрый доступ», режим root (корень / , переключатель, только чтение по умолчанию; запись — отдельный чекбокс)	760×500
Блокнот	многострочный текст, Файл/Правка/Формат, SAF, * при изменении, строка состояния	640×420
Калькулятор	стандартный, история 10 записей, ввод клавишами	320×480
Параметры	раздел 18	820×540
Диспетчер задач	вкладки «Процессы» (окна из TaskRepo + процессы ps/RAM dumpsys meminfo), «Производительность» (CPU /proc/stat, RAM /proc/meminfo, батарея), кнопки «Закрыть окно», «Снять задачу» (am force-stop)	600×440
Терминал	строка команд, выполнение через RootShell (переключатель su/обычный shell), вывод прокручиваемый	640×420
Браузер	WebView, адресная строка, назад/вперёд/обновить, поиск DuckDuckGo, 1 вкладка	820×540
Рисование	кисть/ластик, палитра 12, толщины 1/3/6/12, очистить, PNG	640×480
Часы	часы, секундомер, таймер	360×320
Выполнить	поле: пакет/URL/путь/команда su:	420×160 над панелью
18. Параметры (содержимое)
Левая колонка: Система, Персонализация, Приложения, Экран и окна, Root и безопасность, О планшете.
Система: яркость/громкость, Wi-Fi/BT/данные (плитки как в QuickOverlay), таймаут экрана.
Персонализация: тема (светлая/тёмная), акцент (8 цветов), обои, выравнивание панели, значки Поиск/TaskView/Виджеты вкл/выкл.
Приложения: список установленных, закрепление, удаление, «Принудительно оконный режим для всех» (forceFreeform).
Экран и окна: масштаб (раздел 13.3), скрывать статус-бар, макс. число Chrome, стратегия парковки (Авто/S1/S2/S3), анимации вкл/выкл.
Root и безопасность: статус su/helper, авто-выдача прав (повторить), «Аварийное восстановление», «Экспорт калибровки».
О планшете: модель, Android, root-менеджер (если определился), RAM, версия лаунчера.
19. Дизайн-токены (Tokens.kt)
Размеры (px)
TASKBAR_H=48, TASKBAR_ICON=24, TASKBAR_ITEM_W=44, TASKBAR_ITEM_H=40, TASKBAR_ITEM_GAP=4, TASKBAR_ITEM_RADIUS=4, IND_ACTIVE_W=16, IND_IDLE_W=6, IND_H=3, TRAY_W=140, SHOW_DESKTOP_W=6, WINDOW_TITLE_H=32 (используется если Cal.captionH неизвестен), CAPTION_BTN_W=46, WINDOW_MIN_W=280, WINDOW_MIN_H=180, START_W=560, START_H=620, START_BOTTOM_MARGIN=8, START_RADIUS=8, START_TILE=72, QUICK_W=360, QUICK_H=400, NOTIF_W=380, SNAP_EDGE=8, SNAP_CORNER=48, SWITCH_CARD_W=160, SWITCH_CARD_H=120, TASKVIEW_CARD_W=240, TASKVIEW_CARD_H=180, THUMB_W=320, THUMB_H=240, MENU_ITEM_H=36, MENU_MIN_W=200, MENU_RADIUS=8, ICON_CELL_W=88, ICON_CELL_H=96, DESKTOP_ICON=40, MAX_CHROME=8.
Цвета (тёмная тема)
BG_TASKBAR #E6202020, BG_START #F2202020, BG_PANEL #F22C2C2C, BG_WINDOW #FF202020, BG_TITLE_ACTIVE #FF2B2B2B, BG_TITLE_INACTIVE #FF202020, FG_PRIMARY #FFFFFFFF, FG_SECONDARY #B3FFFFFF, HOVER #1AFFFFFF, PRESSED #0DFFFFFF, ACCENT #FF60CDFF, CLOSE_HOVER #FFC42B1C, CLOSE_PRESSED #FFB22A1B, BORDER #33FFFFFF, SNAP_PREVIEW #4D60CDFF (+ рамка ACCENT 2 px, радиус 8), DIM #99000000.
Светлая: BG_TASKBAR #E6F3F3F3, BG_START #F2F3F3F3, BG_PANEL #F2FFFFFF, BG_WINDOW #FFFFFFFF, BG_TITLE_ACTIVE #FFF3F3F3, BG_TITLE_INACTIVE #FFFFFFFF, FG_PRIMARY #FF1B1B1B, FG_SECONDARY #99000000, HOVER #0F000000, PRESSED #08000000, ACCENT #FF0067C0.
Тайминги (мс)
ANIM_OPEN=167, ANIM_CLOSE=120, ANIM_MINIMIZE=200, ANIM_RESTORE=200, ANIM_MAX=180, ANIM_STEPS=5, ANIM_START_OPEN=220, ANIM_SNAP_PREVIEW=150, DOUBLE_TAP=300, LONG_PRESS=500, TOOLTIP_DELAY=700, SWITCH_SHOW_DELAY=200, ROTATION_HIDE=250, PERSIST_DEBOUNCE=400, DRAG_FRAME=16, PARK_VERIFY=300, THUMB_MIN_INTERVAL=2000, THUMB_TTL_ANIM=5000, HELPER_POLL=200, DPI_REVERT=15000, ROOT_TIMEOUT=4000.
Шрифты: sans-serif; sp: заголовок окна 12, подписи 12, меню 13, заголовки панелей 14, часы 12.
Иконки: собственные векторные 24×24: start, search, taskview, widgets, min, max, restore, close, wifi, bluetooth, volume, battery, brightness, settings, lock, power, folder, file, back, forward, up, refresh, plus, airplane, data, torch, rotate, dnd, bell.
20. Структура файлов
app/src/main/java/ru/winlauncher/
  App.kt  Tokens.kt  Cal.kt  MainActivity.kt  RecoveryActivity.kt  RootLabActivity.kt
  core/    Prefs.kt  AppRepo.kt  PermissionHelper.kt  AutoGrant.kt  Hotkeys.kt  Rects.kt
  root/    RootShell.kt  RootHelper.kt  HelperMain.kt  Protocol.kt  StackParser.kt
  tasks/   TaskModel.kt  TaskRepo.kt  WindowManagerX.kt  ParkStrategy.kt  DesktopsManager.kt
  chrome/  ChromeManager.kt  ChromeView.kt
  overlay/ OverlayHost.kt  TaskbarOverlay.kt  StartOverlay.kt  QuickOverlay.kt  NotifOverlay.kt
           SwitcherOverlay.kt  TaskViewOverlay.kt  SnapPreviewOverlay.kt  SnapFlyoutOverlay.kt
           TooltipOverlay.kt  ContextMenuOverlay.kt  RunDialogOverlay.kt  SnipOverlay.kt  ThumbCache.kt
  desktop/ DesktopView.kt  DesktopIcon.kt
  apps/    ExplorerActivity.kt  NotepadActivity.kt  CalcActivity.kt  SettingsActivity.kt
           TaskManagerActivity.kt  TerminalActivity.kt  BrowserActivity.kt  PaintActivity.kt  ClockActivity.kt
  service/ DockService.kt  WindowWatcherService.kt  NotifyService.kt  BootReceiver.kt  RecoverReceiver.kt
res/xml/accessibility_service_config.xml
DECISIONS.md  README.md  ROOT_CALIBRATION.md
21. Этапы (R0 … R15). Один этап = одна сессия.
R0. Каркас, Root-слой, калибровка, восстановление. Проект, Tokens.kt, Cal.kt, Prefs, манифест HOME, RootShell, HelperMain+RootHelper (ops: ping, stacks, resize, remove, front, shell, cap), AutoGrant, RecoveryActivity+RecoverReceiver, RootLabActivity (все тесты раздела 5 с кнопкой «Запустить» и записью calibration.json).
Приёмка: приложение собирается; su подтверждается; stacks возвращает реальные taskId при запуске 3 приложений; в лаборатории все тесты показывают ✅/❌ и JSON сохраняется; Recovery работает; пользователь присылает calibration.json — по нему заполняется DECISIONS.md.
R1. TaskRepo. Парсинг событий stacks, TaskModel, z-порядок, MRU, классификация состояний, диагностический экран (список окон: taskId, pkg, bounds, z, state).
Приёмка: список совпадает с реальностью для 4 открытых окон (в т.ч. 2 окна одного пакета); закрытие убирает запись ≤ 500 мс.
R2. WindowManagerX: действия. launch, front, animateTo, MAX/restore, snap, remove, парковка S1–S3 по Cal, Win-хоткеи окон (через временный экран-пульт с кнопками).
Приёмка: по кнопкам пульта любое окно разворачивается в WORK, делится на половины/четверти, сворачивается и восстанавливается в прежний Rect, закрывается.
R3. Chrome. ChromeManager, ChromeView, перекрытие Samsung-caption, hover/pressed, drag, double-click, кнопки, видимая часть (вычитание окон), SnapPreviewOverlay, drag к краям/верху/углам.
Приёмка: у 3 перекрывающихся окон шапки корректно обрезаны; перетаскивание плавное (≥ 15 fps); max/snap по drag; нативный caption полностью закрыт.
R4. Панель задач. TaskbarOverlay, кнопки, индикаторы, группировка по пакету, поведение клика (таблица 10.3), Jump List, трей, часы, показать рабочий стол.
Приёмка: 10 сценариев таблицы 10.3 проходят.
R5. Пуск. Раздел 11 полностью (без «Рекомендуем» — заглушка), подъём над IME.
Приёмка: поиск, запуск, закрепление, питание (с подтверждением).
R6. Alt+Tab, Task View, ThumbCache, горячие клавиши. Разделы 12, 15 (кроме столов и скриншота).
Приёмка: Alt+Tab MRU на 4 окнах; Win+Tab показывает миниатюры; Win-стрелки работают глобально поверх Chrome.
R7. Встроенные приложения. Раздел 17 (по очереди; каждое — отдельная задача в freeform).
Приёмка: каждое запускается как окно, управляется Chrome/панелью, не падает.
R8. Рабочий стол. Раздел 14, кнопка Home = показать рабочий стол.
R9. Быстрые настройки и питание. Раздел 13.1, 13.3 (без DPI).
R10. Уведомления и медиа. NotifyService, NotifOverlay, бейджи, плитка медиа в Quick.
R11. Параметры + Диспетчер задач (полные). Разделы 17–18.
R12. Скриншоты, DPI с откатом, Snap Layouts/Assist, всплывающие превью, Tooltip, контекстные меню везде.
R13. (Опционально) Виртуальные рабочие столы и свои ручки ресайза активного окна.
R14. Устойчивость. BootReceiver, crash guard, перезапуск helper, деградация (Cal.degraded), деградация без root-чтения, дрейн батареи (экран off → остановить polling helper), оптимизация памяти.
R15. Полировка и финальный тест по разделу 23.
22. Пограничные случаи
Приложение отказалось от freeform → считается FULLSCREEN, панель видна, Max/Snap недоступны (кнопки Chrome не показываются).
Два окна одного пакета → две задачи, одна группа в панели, превью-ряд.
Окно за экраном после поворота/смены DPI → clampAll(): вернуть в WORK, шапка видна.
Задача с parked=MINIMIZE исчезла → удалить из модели.
Процесс лаунчера убит → после перезапуска TaskRepo перечитывает stacks, parked-окна определяются по bounds (right ≥ W+Cal.parkOffX/2 → MINIMIZED), restoreRect восстановить из Prefs.
Helper умер во время drag → drag отменяется, окно остаётся где было.
IME открыта → Пуск уменьшается, панель остаётся под IME; Chrome не перекрывает IME (скрыть Chrome, если IME-область пересекает шапку).
Шторка/keyguard/выключенный экран → скрыть все оверлеи; helper polling приостановить.
Win-комбинации при фокусе в поле пароля — не перехватывать.
Нажата нативная кнопка Samsung (если Chrome не покрывал) — обычная обработка задач, никаких предположений.
Команда resize вернула ошибку 3 раза подряд → Cal.moveResizeWorks=false, перейти на фолбэк 7.6, показать баннер.
Меньше 300 МБ свободной RAM → Cal.maxChrome=4, ThumbCache ≤ 8, отключить анимации.
23. Тест-матрица (финальная)
Приложения: Chrome, YouTube, Telegram, Gmail, Settings, My Files, Camera, игра, банк, 2 окна Chrome.
Запуск 5 окон; Chrome-шапки видны на всех, перекрытия обрезаны корректно.
Drag всех окон; snap к краям/углам/верху; возврат из snap по drag вниз.
«□»/двойной клик/Win+↑ и обратно (прежний Rect).
«—» → окно исчезло, кнопка в панели, рабочий стол виден; восстановление в прежнее место; Win+D дважды.
«×» и Alt+F4 → окно и кнопка исчезли ≤ 500 мс.
Alt+Tab на 5 окнах, MRU верный; Win+Tab миниатюры.
Панель: клик по активной сворачивает, по свёрнутой восстанавливает, группа из 2 окон.
Пуск: поиск (+клавиатура), закрепить, запуск; клавиатура остаётся рабочей в приложении после закрытия.
Быстрые настройки: Wi-Fi, Bluetooth, данные, авиарежим, яркость, громкость — реально меняются.
Скриншот, Win+Shift+S.
Поворот, смена DPI с автооткатом.
Экран off/on, keyguard, перезагрузка планшета → лаунчер поднимается сам.
Убийство DockService и HelperMain → автовосстановление ≤ 5 с.
Экран «Аварийное восстановление» возвращает систему в исходное состояние (статус-бар, DPI, окна в видимой области).
Нагрузка: 8 окон, 20 минут, без роста памяти > 150 МБ у лаунчера.
24. Анти-паттерны (запрещено)
Хардкод чисел/цветов/Cal-значений вне Tokens.kt/Cal.kt.
Частые am/input/dumpsys через RootShell (использовать helper).
Блокирующие root-вызовы на UI-потоке.
Таймерный опрос am stack list; опрос — только внутри helper, только при изменениях.
Пересоздание ChromeView на каждый кадр; одновременные параллельные resize одного taskId.
Логирование содержимого окон/уведомлений.
Изменение системных настроек без записи в «откат» (policy_control, density, size — каждое с кнопкой отката).
Любые функции вне плана (патчи SystemUI, Xposed, Magisk-модули, второй монитор).
25. Шаблон запроса к исполнителю
Контекст: WinLauncher ROOT. Файл WinLauncher-ROOT-master-plan.md (и ROOT_CALIBRATION.md/DECISIONS.md, если есть) приложены.
Задача: выполнить ТОЛЬКО этап Rx.
Правила: раздел 0, числа из раздела 19, Cal-значения из Cal.kt, root только через RootShell/RootHelper.
Результат: код + отчёт (раздел 0, п. 10). Если чего-то не хватает — простейшее решение + строка в DECISIONS.md.
