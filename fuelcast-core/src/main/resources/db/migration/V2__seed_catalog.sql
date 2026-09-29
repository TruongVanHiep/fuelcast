-- Dữ liệu danh mục. Khớp đúng với product code mà MoitBulletinParser trả về.

INSERT INTO fuel_product (code, name_vi, unit, is_gasoline, display_order) VALUES
    ('E5RON92',  'Xăng E5RON92',            'lít', TRUE,  1),
    ('RON95',    'Xăng RON95-III / E10RON95-III', 'lít', TRUE,  2),
    ('DO_005S',  'Dầu điêzen 0.05S',        'lít', FALSE, 3),
    ('KEROSENE', 'Dầu hỏa',                 'lít', FALSE, 4),
    ('FO_180',   'Dầu madút 180CST 3.5S',   'kg',  FALSE, 5)
ON CONFLICT (code) DO NOTHING;

-- RON95 cố ý gộp RON95-III và E10RON95-III thành một chuỗi giá liên tục:
-- E10 thay thế RON95-III từ 2026 ở cùng vị trí thị trường, tách ra thì mỗi
-- chuỗi đều quá ngắn để dùng được.

INSERT INTO price_publisher (code, name_vi, type, website) VALUES
    ('MOIT',       'Bộ Công Thương',                  'REGULATOR', 'https://moit.gov.vn'),
    ('PETROLIMEX', 'Tập đoàn Xăng dầu Việt Nam',      'TRADER',    'https://www.petrolimex.com.vn')
ON CONFLICT (code) DO NOTHING;
