-- DEMO DATA ONLY. Loaded only with the "demo" Spring profile (see application-demo.yml).
-- Never enabled in production. All names are fictional.

INSERT INTO plants (code, name, city, country, daily_capacity, status) VALUES
    ('JP-KNT-01', 'Kanto Assembly Plant',     'Yokohama',   'Japan',          1200, 'ACTIVE'),
    ('JP-KYU-01', 'Kyushu Assembly Plant',    'Fukuoka',    'Japan',           900, 'ACTIVE'),
    ('UK-NE-01',  'North East Vehicle Plant', 'Sunderland', 'United Kingdom',  800, 'ACTIVE'),
    ('US-TN-01',  'Tennessee Assembly Plant', 'Nashville',  'United States',   650, 'MAINTENANCE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO vehicle_models (model_code, name, category, powertrain, launch_year, base_price, active) VALUES
    ('AUR-EV',   'Aurora EV',      'CROSSOVER',    'ELECTRIC', 2025, 4250000.00, TRUE),
    ('RDG-SUV',  'Ridge SUV',      'SUV',          'HYBRID',   2023, 3890000.00, TRUE),
    ('MTR-HB',   'Metro Hatch',    'HATCHBACK',    'PETROL',   2021, 1980000.00, TRUE),
    ('HAUL-PRO', 'Haul Pro',       'PICKUP_TRUCK', 'DIESEL',   2019, 3450000.00, TRUE),
    ('CITY-VAN', 'City Van (old)', 'VAN',          'DIESEL',   2014, 2600000.00, FALSE)
ON CONFLICT (model_code) DO NOTHING;

INSERT INTO production_orders
    (order_number, plant_id, vehicle_model_id, quantity, completed_quantity, status, priority,
     planned_start_date, planned_end_date, actual_start_at, notes)
VALUES
    ('PO-SEED-0001',
     (SELECT id FROM plants WHERE code = 'JP-KNT-01'),
     (SELECT id FROM vehicle_models WHERE model_code = 'AUR-EV'),
     6000, 2400, 'IN_PROGRESS', 'HIGH',
     CURRENT_DATE - 3, CURRENT_DATE + 7, now() - INTERVAL '3 days',
     'Launch batch for domestic dealers'),
    ('PO-SEED-0002',
     (SELECT id FROM plants WHERE code = 'UK-NE-01'),
     (SELECT id FROM vehicle_models WHERE model_code = 'RDG-SUV'),
     3200, 0, 'PLANNED', 'MEDIUM',
     CURRENT_DATE + 10, CURRENT_DATE + 20, NULL,
     'European market allocation'),
    ('PO-SEED-0003',
     (SELECT id FROM plants WHERE code = 'JP-KYU-01'),
     (SELECT id FROM vehicle_models WHERE model_code = 'MTR-HB'),
     1800, 0, 'PLANNED', 'LOW',
     CURRENT_DATE + 5, CURRENT_DATE + 9, NULL,
     NULL)
ON CONFLICT (order_number) DO NOTHING;
