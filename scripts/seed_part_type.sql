BEGIN TRANSACTION;

INSERT INTO part_type (id, code, label, people_count, sex_rule, fixed, sort_order)
VALUES
    ('pt_LETTURA_BIBLICA', 'LETTURA_BIBLICA', 'Lettura biblica', 1, 'UOMO', 1, 0),
    ('pt_INIZIARE_CONVERSAZIONE', 'INIZIARE_CONVERSAZIONE', 'Iniziare una conversazione', 2, 'LIBERO', 0, 1),
    ('pt_FARE_REVISITA', 'FARE_REVISITA', 'Fare una revisita', 2, 'LIBERO', 0, 2),
    ('pt_STUDIO_BIBLICO', 'STUDIO_BIBLICO', 'Tenere uno studio biblico', 2, 'LIBERO', 0, 3),
    ('pt_SPIEGARE_CIO_CHE_SI_CREDE', 'SPIEGARE_CIO_CHE_SI_CREDE', 'Spiegare quello in cui si crede', 2, 'LIBERO', 0, 4),
    ('pt_DISCORSO', 'DISCORSO', 'Discorso', 1, 'UOMO', 0, 5),
    ('pt_DISCORSO_CON_UDITORIO', 'DISCORSO_CON_UDITORIO', 'Discorso con domande dall''uditorio', 1, 'UOMO', 0, 6)
ON CONFLICT(code) DO UPDATE SET
    label = excluded.label,
    people_count = excluded.people_count,
    sex_rule = excluded.sex_rule,
    fixed = excluded.fixed,
    sort_order = excluded.sort_order;

COMMIT;
