ALTER TABLE published_object
    ALTER COLUMN validity_not_before SET NOT NULL,
    ALTER COLUMN validity_not_after SET NOT NULL;
