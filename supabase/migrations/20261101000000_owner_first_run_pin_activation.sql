DO $$
DECLARE
  affected_rows integer;
BEGIN
  UPDATE private.account_login_aliases AS aliases
  SET must_change_pin = true
  FROM public.platform_owners AS owners
  WHERE aliases.user_id = owners.user_id
    AND owners.active = true
    AND aliases.account_id = 'SIMSWIAH1'
    AND aliases.active = true;
  GET DIAGNOSTICS affected_rows = ROW_COUNT;
  IF affected_rows <> 1 THEN
    RAISE EXCEPTION 'Expected exactly one active Platform Owner account alias; updated % rows', affected_rows;
  END IF;
END
$$;