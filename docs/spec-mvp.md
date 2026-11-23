# Spec: Mười Kip POS — Bản MVP (chạy trên Sunmi T1-G)

> Trạng thái: `ready-for-agent` (chưa có issue tracker — spec lưu tạm trong repo, chuyển lên tracker khi cấu hình xong).
> Nguồn quyết định: phiên grilling ngày 2026-11-23.

## Thuật ngữ

| Thuật ngữ | Nghĩa |
|---|---|
| **Khu** (Zone) | Nhóm bàn: Sân trước, Nhà gỗ, Sân sau, VIP. Mỗi khu là *khu thường* hoặc *khu tính giờ*. |
| **Bàn** (Table) | Một bàn hoặc một phòng thuộc một khu. Ở khu tính giờ, mỗi bàn là một phòng VIP có đơn giá/giờ riêng. |
| **Đơn bàn** (Order) | Phiên phục vụ của một bàn, mở khi gọi món đầu tiên (hoặc khi mở phòng VIP) và đóng khi thanh toán. |
| **Dòng món** (Order line) | Một món trong đơn bàn: tên, số lượng, đơn giá áp dụng (có thể chưa có). |
| **Món giá nhập khi bán** | Món không có giá mặc định (Gà, Gà lá dang). Phải nhập giá trước khi thanh toán. |
| **Tiền phòng** | Phí theo thời gian của bàn thuộc khu tính giờ. |
| **Tạm tính** | Phiếu in để khách kiểm tra, không đóng bàn. |
| **Hóa đơn** | Phiếu in khi thanh toán, đóng bàn. |

## Problem Statement

Quán nhậu Mười Kip hiện tính tiền bằng tay. Nhân viên ghi giấy món khách gọi, cộng tiền thủ công, khó theo dõi bàn nào đang có khách, bàn nào đã gọi gì, và phòng VIP ngồi bao lâu. Ba mẹ (khoảng 50 tuổi) là người đứng quầy, nên mọi công cụ phải cực kỳ đơn giản. Quán đã có máy Sunmi T1-G (Android 6.0.1, màn 14" Full HD, máy in 80mm có dao cắt) nhưng chưa có phần mềm phù hợp: các app POS phổ biến hoặc không chạy trên Android 6, hoặc quá phức tạp, hoặc phụ thuộc internet.

## Solution

Một app Android chạy trên chính máy T1-G, hoạt động hoàn toàn không cần internet. Màn hình chính là sơ đồ bàn theo khu. Chạm vào bàn để gọi món từ menu của quán, chạm vào giá để đổi giá. Phòng VIP tự tính tiền theo giờ. Khi khách về, thu ngân bấm thanh toán, nhập tiền khách đưa, app tính tiền thối và in hóa đơn có tên, địa chỉ, số điện thoại quán. Chữ to, nút to, chỉ chạm, mọi thao tác xóa đều hỏi lại. Dữ liệu lưu trong máy, tắt máy không mất.

## User Stories

### Sơ đồ bàn
1. Là thu ngân, tôi muốn thấy toàn bộ bàn của một khu trên một màn hình, để biết ngay tình trạng quán.
2. Là thu ngân, tôi muốn chuyển giữa các khu bằng các tab to ghi tên khu (Sân trước, Nhà gỗ, Sân sau, VIP), để không phải tìm.
3. Là thu ngân, tôi muốn bàn trống và bàn có khách khác màu rõ rệt, để nhìn từ xa cũng phân biệt được.
4. Là thu ngân, tôi muốn mỗi bàn có khách hiện tổng tiền tạm tính, để trả lời khách nhanh.
5. Là thu ngân, tôi muốn phòng VIP đang có khách hiện thời gian đã ngồi, để biết khách ngồi bao lâu.
6. Là thu ngân, tôi muốn bàn chỉ hiện số (1, 2, 3…) còn tên khu nằm trên tab, để chữ trên ô bàn to và dễ đọc.
7. Là thu ngân, tôi muốn app mở lại đúng trạng thái các bàn sau khi tắt máy hoặc mất điện, để không mất đơn đang phục vụ.

### Thêm khu, thêm bàn
8. Là chủ quán, tôi muốn có nút "+ Thêm khu", để mở thêm khu mới khi quán mở rộng.
9. Là chủ quán, tôi muốn khi thêm khu được chọn *khu thường* hoặc *khu tính giờ*, để khu VIP mới cũng tính tiền phòng.
10. Là chủ quán, tôi muốn có nút "+ Thêm bàn" trong mỗi khu, bàn mới tự đánh số tiếp theo, để thêm bàn không phải gõ gì.
11. Là chủ quán, khi thêm bàn ở khu tính giờ, tôi muốn nhập đơn giá/giờ của phòng đó, để mỗi phòng có giá riêng.

### Gọi món
12. Là thu ngân, tôi muốn chạm vào một bàn để mở màn gọi món của bàn đó, chỉ bằng một lần chạm.
13. Là thu ngân, tôi muốn menu chia theo nhóm (Món xào/chiên, Nướng, Gà, Lẩu, Đồ uống) bằng các tab to, để tìm món nhanh.
14. Là thu ngân, tôi muốn mỗi món hiện tên và một giá, chạm một lần là thêm 1 phần vào bàn.
15. Là thu ngân, tôi muốn chạm lại món đã có trong bàn thì tăng số lượng thay vì thêm dòng mới, để hóa đơn gọn.
16. Là thu ngân, tôi muốn có nút + và − to trên mỗi dòng món, để chỉnh số lượng.
17. Là thu ngân, tôi muốn khi giảm số lượng về 0 hoặc bấm xóa món thì app hỏi xác nhận, để không lỡ tay xóa.
18. Là thu ngân, tôi muốn chạm vào giá của một dòng món để đổi giá bằng bàn phím số to, để xử lý món giá dao động (tôm, ếch, ba ba theo ký…).
19. Là thu ngân, tôi muốn việc đổi giá chỉ áp dụng cho dòng món trong bàn đó, không đổi giá gốc trong menu.
20. Là thu ngân, tôi muốn bàn phím số có các nút nhanh "000" và xóa lùi, để nhập tiền như 150.000 nhanh.
21. Là thu ngân, tôi muốn thêm món "giá nhập khi bán" (Gà, Gà lá dang) mà được bỏ qua nhập giá, để không làm chậm lúc khách đang gọi.
22. Là thu ngân, tôi muốn dòng món chưa có giá hiện màu đỏ với chữ "Chưa nhập giá", để không quên.
23. Là thu ngân, tôi muốn thấy tổng tiền tạm tính của bàn luôn hiện to ở góc màn gọi món.
24. Là thu ngân, tôi muốn có nút quay về sơ đồ bàn luôn ở cùng một chỗ, để không bị lạc.

### Phòng VIP tính giờ
25. Là thu ngân, tôi muốn chạm vào phòng VIP trống thì đồng hồ bắt đầu chạy, để không phải nhớ giờ khách vào.
26. Là thu ngân, tôi muốn thấy giờ vào và thời gian đã ngồi trên màn gọi món của phòng.
27. Là thu ngân, tôi muốn tiền phòng tự tính theo đơn giá/giờ, làm tròn lên mỗi 15 phút, tối thiểu 1 giờ.
28. Là thu ngân, tôi muốn sửa được số tiền phòng trước khi in hóa đơn, để bớt cho khách quen.
29. Là thu ngân, tôi muốn hóa đơn có dòng riêng "Tiền phòng VIP 2 – 2 giờ 15 phút", để khách hiểu mình trả gì.
30. Là thu ngân, tôi muốn phòng VIP vẫn gọi món như bàn thường.

### Tạm tính và thanh toán
31. Là thu ngân, tôi muốn bấm "In tạm tính" để in phiếu cho khách kiểm tra, bàn vẫn mở.
32. Là thu ngân, tôi muốn bấm "Thanh toán" để sang màn thanh toán của bàn.
33. Là thu ngân, khi bấm Thanh toán mà còn món chưa có giá, tôi muốn app đưa thẳng tôi tới bàn phím nhập giá của món đó, để không thể in hóa đơn sai.
34. Là thu ngân, tôi muốn màn thanh toán hiện tổng tiền bằng chữ thật to.
35. Là thu ngân, tôi muốn có ô "Khách đưa" với các nút nhanh "Đủ tiền", 100K, 200K, 500K, để không phải gõ.
36. Là thu ngân, tôi muốn app tự tính và hiện to tiền thối.
37. Là thu ngân, tôi muốn app không cho bấm "Thanh toán & In" khi tiền khách đưa ít hơn tổng tiền.
38. Là thu ngân, tôi muốn bấm "Thanh toán & In" thì in hóa đơn và bàn trở về trống.
39. Là thu ngân, tôi muốn hủy màn thanh toán để quay lại gọi thêm món, bàn vẫn giữ nguyên.
40. Là chủ quán, tôi muốn đơn đã thanh toán được lưu lại trong máy, để sau này làm báo cáo doanh thu.

### Hóa đơn in
41. Là chủ quán, tôi muốn đầu hóa đơn in "MƯỜI KIP", địa chỉ "Lam Phụng, Đại Đồng, Đại Lộc, Quảng Nam" và số điện thoại 0989824694.
42. Là thu ngân, tôi muốn hóa đơn ghi khu, số bàn, giờ vào, giờ in.
43. Là thu ngân, tôi muốn mỗi dòng hóa đơn có tên món, số lượng, đơn giá, thành tiền.
44. Là thu ngân, tôi muốn hóa đơn có tổng tiền, tiền khách đưa và tiền thối.
45. Là thu ngân, tôi muốn phiếu tạm tính ghi rõ chữ "TẠM TÍNH", để khách không nhầm với hóa đơn.
46. Là thu ngân, tôi muốn máy tự cắt giấy sau khi in.
47. Là thu ngân, khi máy in hết giấy hoặc lỗi, tôi muốn app báo rõ bằng tiếng Việt và cho in lại, để không mất hóa đơn; bàn vẫn được đóng đúng như đã thanh toán.
48. Là thu ngân, tôi muốn số tiền hiển thị và in theo kiểu Việt Nam (150.000đ).

### Khả dụng cho người lớn tuổi
49. Là thu ngân 50 tuổi, tôi muốn toàn bộ app bằng tiếng Việt có dấu.
50. Là thu ngân 50 tuổi, tôi muốn chữ tối thiểu ~20sp, nút to, màu tương phản cao.
51. Là thu ngân 50 tuổi, tôi muốn chỉ cần chạm, không phải vuốt hay nhấn giữ.
52. Là thu ngân 50 tuổi, tôi muốn mọi việc làm xong trong tối đa 2 lần chạm từ màn chính.
53. Là thu ngân 50 tuổi, tôi muốn không phải đăng nhập hay nhớ mật khẩu.
54. Là thu ngân, tôi muốn app chạy được khi quán mất internet.

## Implementation Decisions

### Nền tảng
- Ứng dụng Android native viết bằng Kotlin + Jetpack Compose. Tên app "Mười Kip POS", package `vn.muoikip.pos`.
- **minSdk 23** (T1-G chạy Android 6.0.1). Mọi thư viện AndroidX phải được ghim ở phiên bản còn hỗ trợ API 23. AndroidX hiện mặc định minSdk 24; Room 2.8+ yêu cầu 23 và Room 2.7.x hỗ trợ 21. Compose BOM phải được ghim ở bản cuối cùng còn hỗ trợ API 23, phải kiểm chứng bằng việc cài thật lên T1-G ngay từ đầu (rủi ro số 1).
- Màn hình mục tiêu duy nhất: ngang (landscape), 1920×1080, 14". App khóa hướng ngang.
- Không có đăng nhập, không có mạng ở MVP.

### Mô-đun
1. **Lõi nghiệp vụ (`PosEngine`)**: Kotlin thuần, không phụ thuộc Android. Đây là seam test duy nhất. Nó nhận các lệnh và trả về trạng thái:
   - Truy vấn: danh sách khu, bàn theo khu kèm trạng thái (trống / có khách, tổng tạm tính, thời gian ngồi), menu theo nhóm, đơn của một bàn.
   - Lệnh: thêm khu, thêm bàn, thêm món vào bàn, đặt số lượng, xóa dòng món, đổi giá dòng món, nhập giá cho món chưa có giá, mở phòng tính giờ, sửa tiền phòng, tạo tạm tính, thanh toán (với tiền khách đưa).
   - Kết quả thanh toán và tạm tính là một **mô hình hóa đơn** độc lập với máy in (danh sách dòng có cấu trúc). Thanh toán bị từ chối với lý do cụ thể nếu còn món chưa có giá (kèm danh sách dòng cần nhập) hoặc tiền khách đưa không đủ.
   - Phụ thuộc được tiêm vào: **đồng hồ**, **kho dữ liệu**, **máy in**.
2. **Kho dữ liệu**: interface trong lõi; hiện thực bằng Room (SQLite) trên máy và bằng bản trong bộ nhớ cho test.
3. **Máy in**: interface `ReceiptPrinter` nhận mô hình hóa đơn. Hiện thực trên máy dùng dịch vụ AIDL của Sunmi (`woyou.aidlservice.jiuiv5`, `IWoyouService`), định dạng khổ 80mm (khoảng 48 ký tự/dòng, cỡ chữ thường), cắt giấy sau khi in. Hàm định dạng hóa đơn thành các dòng văn bản nằm trong lõi để test được.
4. **Giao diện Compose**: lớp mỏng gọi `PosEngine`, không chứa logic tính tiền. Ba màn: Sơ đồ bàn, Gọi món, Thanh toán; cùng các hộp thoại bàn phím số, xác nhận, thêm khu, thêm bàn.

### Mô hình dữ liệu
- **Khu**: id, tên, loại (thường / tính giờ), thứ tự hiển thị.
- **Bàn**: id, khu, số thứ tự, đơn giá/giờ (chỉ có ở khu tính giờ).
- **Nhóm món**: id, tên, thứ tự.
- **Món**: id, nhóm, tên, giá mặc định (có thể rỗng = giá nhập khi bán), đang bán.
- **Đơn bàn**: id, bàn, thời điểm mở, thời điểm đóng, trạng thái (mở / đã thanh toán), tiền phòng đã sửa tay (rỗng nếu tự tính), tiền khách đưa.
- **Dòng món**: id, đơn, món, tên món chụp lại tại thời điểm gọi, số lượng, đơn giá áp dụng (có thể rỗng), thứ tự thêm.
- Tên và giá được chụp vào dòng món để đổi menu sau này không làm sai đơn cũ.
- Mỗi bàn có tối đa một đơn đang mở.
- Tiền lưu bằng số nguyên đồng (VND), không dùng số thực.
- Thiết kế id và bảng sao cho về sau thêm đồng bộ LAN/cloud không phải đổi cấu trúc. Dùng id dạng UUID dạng chuỗi.

### Quy tắc nghiệp vụ
- **Tiền phòng** = đơn giá/giờ × số block 15 phút (làm tròn lên) ÷ 4, tối thiểu 4 block (1 giờ). Thời gian tính từ lúc mở phòng đến lúc thanh toán (hoặc đến lúc in tạm tính, với phiếu tạm tính). Nếu đã sửa tay thì dùng số sửa tay.
- **Tổng tiền** = tổng (số lượng × đơn giá) của các dòng món + tiền phòng.
- **Tiền thối** = tiền khách đưa − tổng tiền. Không cho thanh toán khi tiền thối < 0.
- Không giảm giá, không VAT, không phí phục vụ ở MVP.
- Mở bàn thường xảy ra ngầm khi thêm món đầu tiên. Bàn thường không có món thì vẫn là trống. Phòng tính giờ mở khi chạm vào phòng trống.
- Thanh toán thành công thì đóng đơn trước rồi mới in. Lỗi in không làm mở lại bàn; cho phép in lại hóa đơn vừa thanh toán.

### Dữ liệu khởi tạo
- **Khu**: Sân trước (8 bàn), Nhà gỗ (8 bàn), Sân sau (8 bàn), VIP (khu tính giờ; VIP 1–3, mỗi phòng 100.000đ/giờ — số tạm, chủ quán sửa sau).
- **Menu** (nguồn: thiết kế Canva "menu – Menu Mùa Đông"; giá khoảng lấy mức thấp làm mặc định):
  - Món xào/chiên: Rau xào 30.000 · Hến xào 50.000 · Cá rầm suối chiên giòn 80.000 · Tôm sông 100.000 · Dế 100.000 · Sùng 100.000 · Ve ve xào 100.000 · Nhái xào sả ớt 100.000 · Búp chuối trộn 100.000 · Ếch 100.000 · Ếch đồng 150.000 · Ốc bươu 150.000
  - Nướng & cá: Cá niên 250.000 · Cá leo nướng 250.000 · Cá chình nướng 500.000
  - Gà: Gà (giá nhập khi bán)
  - Lẩu: Cá leo um chuối 250.000 · Cá chuỗi ngọc 250.000 · Cá ngạnh nấu chua 250.000 · Cá chình um chuối 500.000 · Gà lá dang (giá nhập khi bán) · Ba ba 700.000 (giá theo ký, đổi giá khi bán)
  - Đồ uống: Bia Heineken 25.000 · Bia Tiger 20.000
- Thông tin cửa hàng in trên hóa đơn: MƯỜI KIP · Lam Phụng, Đại Đồng, Đại Lộc, Quảng Nam · 0989824694.
- Dữ liệu khởi tạo chỉ nạp khi cơ sở dữ liệu trống.

## Testing Decisions

- **Một seam duy nhất: `PosEngine`.** Test chỉ đi qua các lệnh và truy vấn công khai của nó, kiểm tra hành vi quan sát được (trạng thái bàn, tổng tiền, lý do từ chối, mô hình hóa đơn, nội dung đã gửi tới máy in giả). Không test chi tiết bên trong như cấu trúc bảng hay hàm riêng.
- Test chạy JVM thuần trên laptop (không cần T1, không cần emulator), với:
  - **Đồng hồ giả** điều khiển được, để test tính giờ VIP.
  - **Kho dữ liệu trong bộ nhớ**.
  - **Máy in giả** ghi lại hóa đơn đã in, và có thể giả lập lỗi.
- Các ca cần có tối thiểu:
  - Thêm món, gộp số lượng khi thêm trùng, giảm về 0, xóa.
  - Đổi giá dòng món không làm đổi giá menu.
  - Món chưa có giá: thêm được; thanh toán bị chặn và trả về đúng dòng cần nhập; nhập giá xong thì thanh toán được.
  - Tiền phòng: dưới 1 giờ tính 1 giờ; 1 giờ 1 phút tính 1 giờ 15 phút; đúng mốc 15 phút không làm tròn thêm; sửa tay tiền phòng được ưu tiên.
  - Tạm tính không đóng bàn; thanh toán đóng bàn và bàn trở về trống.
  - Tiền khách đưa thiếu bị từ chối; tiền thối đúng.
  - Lỗi máy in sau khi thanh toán: bàn vẫn đóng, in lại được.
  - Thêm khu tính giờ và thêm bàn tự đánh số tiếp theo.
  - Định dạng hóa đơn 80mm: đầu hóa đơn, dòng món, tiền phòng, tổng, khách đưa, tiền thối, chữ "TẠM TÍNH" ở phiếu tạm tính, tiền định dạng kiểu Việt Nam.
- **Không test tự động**: giao diện Compose và hiện thực máy in Sunmi. Hai phần này kiểm tra tay trên T1-G: cài app, đi hết luồng gọi món → thanh toán → in.
- Chưa có test nào trong repo để làm mẫu; bộ test của `PosEngine` sẽ là mẫu đầu tiên.

## Out of Scope

- Mã VietQR trên hóa đơn (làm ngay sau MVP; thông tin ngân hàng do chủ quán tự nhập).
- Màn sửa menu (thêm/ẩn/sửa giá món), đổi tên/xóa khu và bàn.
- Chuyển bàn, gộp bàn, tách bill.
- App gọi món cho nhân viên và máy chủ LAN trên T1 (phương án B: laptop làm máy chủ).
- Đồng bộ cloud, sao lưu, báo cáo doanh thu.
- Phiếu bếp (nhân viên vẫn ghi giấy mang vào bếp).
- Cách chế biến món, topping, ghi chú món.
- Giảm giá, VAT, phí phục vụ, hóa đơn điện tử.
- Đăng nhập, mã PIN, phân quyền.
- Ngăn kéo tiền, quẹt thẻ.

## Further Notes

- **Rủi ro 1 — chuỗi build cho Android 6:** cần kiểm chứng sớm nhất bằng một app tối thiểu (Compose + Room đã ghim phiên bản) cài lên T1-G. Máy phát triển có Android Studio (JBR 25), SDK platform 37, build-tools 36.
- **Rủi ro 2 — cài qua USB:** cần bật gỡ lỗi USB trên T1-G; dự phòng là chép APK và cài tay.
- Dịch vụ máy in `woyou.aidlservice.jiuiv5` cần được xác nhận có trên máy (`pm list packages woyou`). File AIDL phải khớp model máy theo tài liệu Sunmi.
- Thứ tự sau MVP: VietQR → sửa menu → chuyển bàn → gộp bàn → app nhân viên → tách bill → báo cáo và sao lưu.
- Menu là "Menu Mùa Đông" và sẽ đổi theo mùa, nên màn sửa menu là ưu tiên cao ngay sau VietQR.

## Đợt 2 (2026-11-23) — đã làm

Các mục dưới đây trước nằm ở Out of Scope, nay đã có (TDD, hai seam: `PosEngine` và hợp đồng `PosStore` chạy trên cả kho trong bộ nhớ lẫn Room/SQLite qua Robolectric):

- **DB**: schema Room được xuất vào `app/schemas` và commit; mỗi phiên bản có migration và test migration dựng từ schema cũ (v1 → v2: bảng `setting`; v2 → v3: cột `orders.placeLabel`). Không bao giờ dùng migration phá dữ liệu. Journal mode TRUNCATE để file DB sao chép được.
- **Sao lưu**: tự động mỗi ngày khi mở app (giữ 30 bản) vào `Android/data/vn.muoikip.pos/files/backups`; sao lưu tay và khôi phục trong Cài đặt. Bản sao lưu gộp cả file `-wal` (lỗi tìm thấy trên T1 với bản cài đầu tiên chạy WAL).
- **Sửa menu**: thêm món, sửa tên/giá/nhóm, ẩn/hiện, thêm nhóm. Đơn đang mở giữ tên và giá cũ.
- **VietQR**: mã QR có sẵn số tiền in trên hóa đơn và phiếu tạm tính khi đã nhập tài khoản ngân hàng (chủ quán tự nhập).
- **Chuyển bàn**: chỉ sang bàn trống cùng loại; phòng VIP giữ giờ vào, áp giá phòng mới.
- **Gộp bàn**: gộp món vào bàn đang có khách; không gộp *từ* phòng VIP (tránh mất tiền phòng).
- **Báo cáo ngày**: ngày kinh doanh 6:00 → 6:00; doanh thu, số hóa đơn, tiền phòng, món bán chạy; danh sách hóa đơn và in lại đúng như đã tính.
- **Mã PIN**: chặn màn Cài đặt (không chặn bán hàng); lưu dạng băm SHA-256.
- **Khu & bàn**: đổi tên khu, xóa khu/bàn đang trống, sửa giá phòng.

Còn lại: app gọi món cho nhân viên (máy chủ LAN), đồng bộ cloud, tách bill.
