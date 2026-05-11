# Kế Hoạch Phát Triển: Rust Native Download Manager

## 1. Mục Tiêu Dự Án (Project Goals)
Xây dựng một phần mềm Hỗ trợ Tải xuống (Download Manager) thay thế hoàn toàn phiên bản Kotlin/Compose cũ. Dự án mới phải đạt được các tiêu chí:
- **Siêu nhẹ & Nhanh**: Tối ưu hóa RAM và CPU, chạy ngầm không ảnh hưởng hệ thống.
- **Tốc độ tải tối đa**: Tận dụng khả năng xử lý đa luồng (multi-threading) của Rust để chia nhỏ file và tải song song.
- **Giao diện hiện đại**: Hỗ trợ giao diện tùy biến cao (Dark/Light mode, Animations).
- **Tích hợp sâu vào Trình duyệt**: Hỗ trợ Browser Extension (Chrome, Edge, Firefox) bắt link tải tự động.
- **Đa nền tảng**: Hỗ trợ Windows, macOS, Linux.

---

## 2. Kiến Trúc Công Nghệ (Dự Kiến)

### 2.1. Lõi hệ thống (Core Engine)
- **Ngôn ngữ**: `Rust` 🦀
- **Network HTTP Client**: Dùng thư viện `reqwest` (xử lý kết nối, tải stream bất đồng bộ).
- **Async Runtime**: Dùng thư viện `tokio` (chuẩn công nghiệp của Rust để chạy đa luồng).

### 2.2. Giao diện (User Interface) - [ĐÃ CHỌN: Native Thuần]
- **Công nghệ**: Sử dụng UI Native thuần bằng Rust (Slint hoặc Iced - *sẽ chốt ở phần dưới*). 
- **Đặc điểm**: Đảm bảo hiệu năng đồ họa đỉnh cao, tận dụng GPU, app cực kỳ nhẹ và loại bỏ hoàn toàn cảm giác "Web" khi sử dụng.

### 2.3. Giao tiếp với Trình duyệt (Browser Extension) - [ĐÃ CHỌN: Native Messaging API]
- **Công nghệ**: Giao tiếp qua `stdin/stdout` dựa trên chuẩn Native Messaging của Chrome/Edge/Firefox.
- **Đặc điểm**: Bảo mật cực cao, không cần mở cổng mạng (port). Trình duyệt có khả năng tự động "đánh thức" App Desktop nếu đang tắt để truyền link tải. Bắt buộc đi kèm với bộ cài đặt (Installer) để cấu hình Registry/Manifest OS.

---

## 3. Lộ Trình Phát Triển Chi Tiết (Detailed Roadmap)

### Giai đoạn 1: Xây dựng Lõi Tải Xuống (Core Engine) & Cơ Sở Dữ Liệu
*Tiêu chí: Hoạt động hoàn toàn qua Command Line (CLI), tối đa hóa tốc độ tải.*
- **Khởi tạo**: Dựng project Rust với `cargo`.
- **Tải cơ bản**: Logic tải 1 file dùng `reqwest` + `tokio`.
- **Tải đa luồng (Multi-threading)**: Thuật toán cắt file (Chunking) thành 8/16/32 phần và tải song song.
- **Quản lý file tạm**: Ghi các chunk vào disk và thuật toán ghép file (Merge) tối ưu I/O.
- **Lưu trữ trạng thái**: Lưu lịch sử và trạng thái (Pause/Resume/Error) vào Database.

### Giai đoạn 2: Xây Dựng Kênh Giao Tiếp Native Messaging
*Tiêu chí: Trình duyệt và App nói chuyện thành công, bắt link tự động.*
- **App Rust**: Code luồng đọc/ghi 4-byte header từ `stdin/stdout`.
- **Browser Extension**: Tạo extension cơ bản (Manifest V3), thêm tính năng chặn (intercept) request tải xuống.
- **Setup Scripts**: Viết file `manifest.json` cho OS và các script `.bat`/`.sh` để đăng ký thử nghiệm (vào Registry Windows / Linux folder).
- **Truyền dữ liệu**: Truyền cục JSON chứa URL, Headers, Cookies từ Extension sang App.

### Giai đoạn 3: Phát Triển Giao Diện Native (UI)
*Tiêu chí: Giao diện mượt mà, render bằng GPU, có Dark/Light mode.*
- Dựng bố cục chính (Main Window): Danh sách file đang tải, thanh tiến trình (Progress bar).
- Giao diện cửa sổ nhỏ (Dialog popup): Hiện ra khi Extension bắn link về để xác nhận (chọn thư mục lưu, tên file).
- Tích hợp Core logic vào UI: Cập nhật UI theo thời gian thực dựa trên tiến độ tải của Core.
- System Tray: Chạy ngầm dưới khay hệ thống, click đúp để mở.

### Giai đoạn 4: Hoàn Thiện & Đóng Gói (Installer & Deployment)
*Tiêu chí: Cài đặt dễ dàng (1-click), tự động cấu hình cho Native Messaging.*
- Viết kịch bản đóng gói Installer (vd: dùng WiX Toolset, Inno Setup hoặc NSIS cho Windows).
- Khi cài đặt: Tự động ghi key vào Registry để Chrome/Edge nhận diện App.
- Chức năng tự động khởi động cùng hệ thống.
- Đóng gói Browser Extension đẩy lên Store.

---

## 4. Lưu ý & Tiêu Chuẩn Kỹ Thuật (Technical Standards)
- **Chuẩn Native Messaging**: Luôn phải quản lý chính xác 4-byte độ dài của message gửi/nhận, nếu sai 1 byte app sẽ crash.
- **Quản lý luồng (Threading)**: UI Thread và Download Thread phải tách biệt hoàn toàn để giao diện không bao giờ bị đơ (freeze). Dùng message passing (channel) để giao tiếp giữa Core và UI.
- **Tối ưu ổ cứng (Disk I/O)**: Không tải thẳng vào RAM (sẽ tràn RAM), mà phải stream thẳng xuống các file tạm trên ổ cứng.

---

## 5. CÁC QUYẾT ĐỊNH CUỐI CÙNG (Finalized)
Dựa trên phản hồi, dự án sẽ được xây dựng theo kiến trúc sau:

1. **Framework UI Native**: **Slint**
   - *Lý do*: Hiệu suất cao, giao diện hiện đại, dễ viết hơn Iced và có hỗ trợ xem trước UI (Preview).
2. **Cơ sở dữ liệu**: **SQLite**
   - *Lý do*: Đảm bảo toàn vẹn dữ liệu, hỗ trợ truy vấn lịch sử tải xuống lớn một cách nhanh chóng.
3. **Mô hình chạy**: **Chạy ngầm (System Tray / Daemon)**
   - *Lý do*: Theo đúng tiêu chuẩn của một trình quản lý tải xuống chuyên nghiệp (như IDM), luôn sẵn sàng nhận lệnh từ trình duyệt.
4. **Kết nối trình duyệt**: **Native Messaging API**
   - *Lý do*: Bảo mật cao nhất và có khả năng tự động đánh thức ứng dụng.

---
*Kế hoạch đã hoàn tất. Bắt đầu giai đoạn khởi tạo dự án.*
