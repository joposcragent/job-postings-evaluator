CREATE OR REPLACE FUNCTION job_postings.cosine_similarity(a real[], b real[])
RETURNS real AS $$
DECLARE
    dot_product real := 0;
    norm_a real := 0;
    norm_b real := 0;
    i integer;
BEGIN
    IF a IS NULL OR b IS NULL OR array_length(a, 1) IS DISTINCT FROM array_length(b, 1) THEN
        RETURN NULL;
    END IF;

    FOR i IN 1..array_length(a, 1) LOOP
        dot_product := dot_product + a[i] * b[i];
        norm_a := norm_a + a[i] * a[i];
        norm_b := norm_b + b[i] * b[i];
    END LOOP;

    IF norm_a = 0 OR norm_b = 0 THEN
        RETURN 0;
    END IF;

    RETURN dot_product / (sqrt(norm_a) * sqrt(norm_b));
END;
$$ LANGUAGE plpgsql IMMUTABLE;
