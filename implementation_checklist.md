# Flow x IDM Parity - Implementation Checklist (Rust + Slint)

Muc tieu: chot cac hang muc uu tien cao de Flow dat trai nghiem "dung duoc hang ngay" truoc, sau do mo rong parity nang cao voi IDM.

## P0 - UI wiring va thao tac cot loi

- [ ] Confirm Download Dialog hoan chinh
  - Files: `app/flow-ui/ui/main.slint`, `app/flow-ui/src/main.rs`, `app/flow-core/src/storage.rs`
  - Lam:
    - Mo rong `AddUrlDialog` thanh luong "confirm": URL, file name, output dir, category/queue, start now/queue later.
    - Bind du lieu 2 chieu giua Slint callback va Rust handler (khong de callback placeholder).
    - Luu thong tin queue/category vao `queue_jobs` khi enqueue.
  - Done when:
    - Bat duoc URL tu tay hoac clipboard deu mo dialog xac nhan day du truong.
    - Bấm "Download Now" -> status `Queued`; "Queue Later" -> status `Paused`.
    - Task moi hien ngay trong bang khong can restart app.

- [ ] Menu Tools/Help day du theo parity
  - Files: `app/flow-ui/ui/main.slint`, `app/flow-ui/src/main.rs`
  - Lam:
    - Them menu top-level `Tools` va cac item: Browser Integration, Per-host Settings, Settings.
    - Bo sung Help item: Check Updates, Donate, Translators, About.
    - Chuyen callback dang `set_status_message("...not wired yet")` sang handler that su.
  - Done when:
    - Tat ca item menu co action thuc te (mo tab/dialog/link), khong con "not wired yet".
    - Keyboard/mouse interaction khong bi lock popup.

- [ ] Download table: them cot Date Added + sort clickable
  - Files: `app/flow-ui/ui/main.slint`, `app/flow-ui/src/main.rs`, `app/flow-core/src/storage.rs`
  - Lam:
    - Them cot `Date Added` vao header + row.
    - Header clickable cho sort (name, size, status, date added).
    - Luu state sort hien tai trong UI state va apply lai khi refresh from DB.
  - Done when:
    - Click header doi huong sort tang/giam.
    - Sort on dinh khi watcher refresh list.
    - Data hien dung theo cot da chon.

## P1 - Tu dong hoa canh tranh truc tiep voi IDM

- [ ] Auto-categorization theo extension + MIME
  - Files: `app/flow-ui/src/main.rs`, `app/flow-core/src/settings.rs`, `app/flow-core/src/download.rs`, `app/flow-core/src/lib.rs`
  - Lam:
    - Them bang rule category (video/audio/docs/archive/apps/others).
    - Suy luan category tu URL/file-name; fallback MIME tu `probe()` header.
    - Tu dong map category -> output folder mac dinh/queue.
  - Done when:
    - `.mp4`, `.mkv` vao Video; `.mp3` vao Music; `.zip` vao Compressed; fallback vao General.
    - User van co the override trong Confirm Dialog.

- [ ] Expose day du settings da co struct
  - Files: `app/flow-ui/ui/main.slint`, `app/flow-ui/src/main.rs`, `app/flow-core/src/settings.rs`
  - Lam:
    - Proxy manual: scheme/host/port/user/pass.
    - Proxy PAC: URL + exclude patterns.
    - Per-host list: host pattern, credentials, user-agent, thread-count.
    - Validation input va save/load 2 chieu.
  - Done when:
    - Sua settings tren UI, restart app van giu gia tri.
    - `per_host_for_url()` va `should_use_manual_proxy()` nhan duoc du lieu dung format.

- [ ] Wiring download actions dang placeholder
  - Files: `app/flow-ui/src/main.rs`
  - Lam:
    - `downloads-select-all`, `downloads-clear-selection`, `downloads-sort-by-name` tu status-only thanh action that.
    - `tasks-start-all`, `tasks-pause-all` can cap nhat DB status + signal downloader.
  - Done when:
    - Action tu menu cho ket qua nhin thay tren UI va DB, khong chi doi status message.

## P2 - Dialogs va cong cu bo sung

- [ ] Batch Download Dialog
  - Files: `app/flow-ui/ui/main.slint`, `app/flow-ui/src/main.rs`, `app/flow-core/src/storage.rs`
  - Lam:
    - Them dialog nhap nhieu URL (paste textarea/file import).
    - Parse/validate URL, bo qua dong rong/trung.
    - Cho phep queue chung queue/category va enqueue hang loat.
  - Done when:
    - Paste 50 URL hop le -> tao du 50 queue jobs.
    - URL sai duoc thong bao ro rang, khong lam fail ca lo.

- [ ] Checksum Tool (MD5/SHA1/SHA256)
  - Files: `app/flow-ui/ui/main.slint`, `app/flow-ui/src/main.rs`, `app/flow-core/src/download.rs`
  - Lam:
    - Them dialog chon file + algo + expected hash (optional).
    - Tinh hash streaming de tranh ngop RAM.
    - Show ket qua + pass/fail compare.
  - Done when:
    - Hash file lon (>= 4GB) van chay on dinh.
    - Compare expected hash cho ket qua dung.

## P2/P3 - Nang cap engine

- [ ] Dynamic parts an toan theo runtime
  - Files: `app/flow-core/src/download.rs`, `app/flow-core/src/queue.rs`
  - Lam:
    - Chia part linh hoat dua theo throughput/remaining bytes.
    - Tranh over-split voi file nho hoac server range kem.
    - Thu thap metric de tune heuristic (min part size, split threshold).
  - Done when:
    - Toc do trung binh khong giam tren workload nho.
    - Workload file lon co cai thien throughput nhat quan.

- [ ] Sparse allocation (feature-gated)
  - Files: `app/flow-core/src/download.rs`, `app/flow-core/src/startup.rs`
  - Lam:
    - Them option sparse theo OS/FS support.
    - Fallback an toan ve preallocation thuong neu khong support.
  - Done when:
    - Tren he thong ho tro: tao file sparse thanh cong.
    - Tren he thong khong ho tro: khong crash, van download binh thuong.

## P0/P1 - Dinh nghia "Done" cap san pham

- [ ] Khong con message "...not wired yet" trong cac action menu/chuc nang chinh.
- [ ] Luong Add URL/Clipboard/Batch deu di qua confirm ro rang va enqueue nhat quan.
- [ ] Sort table hoat dong va on dinh qua refresh DB watcher.
- [ ] Settings save/load 2 chieu day du cho proxy/per-host/user-agent.
- [ ] Test thu cong toi thieu:
  - Add URL (start now + queue later)
  - Clipboard detect -> confirm -> queue
  - Sort 4 cot (name/size/status/date)
  - Batch 20 URL
  - Checksum 1 file nho + 1 file lon

## Thu tu trien khai de xong nhanh

1) Confirm Download Dialog + menu wiring that su
2) Date Added + sort table
3) Auto-categorization
4) Expose settings proxy/per-host
5) Batch Download
6) Checksum Tool
7) Dynamic parts + sparse allocation
