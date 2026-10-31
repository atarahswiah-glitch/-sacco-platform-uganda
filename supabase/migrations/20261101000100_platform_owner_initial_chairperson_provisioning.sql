CREATE OR REPLACE FUNCTION public.provision_sacco_member_login(
  p_sacco_id uuid,
  p_member_row_id uuid,
  p_actor_user_id uuid,
  p_new_user_id uuid,
  p_role text
)
RETURNS TABLE(generated_account_id text, generated_member_id text, generated_member_number text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
declare
  member_row public.sacco_members%rowtype;
  sacco_row public.saccos%rowtype;
  generated_id text;
  attempt integer := 0;
  actor_is_sacco_admin boolean;
  actor_is_owner_bootstrap boolean;
begin
  if p_sacco_id is null or p_member_row_id is null or p_actor_user_id is null or p_new_user_id is null then
    raise exception 'missing_required_identity' using errcode = '22023';
  end if;
  if p_role not in ('chairperson','treasurer','secretary','sacco_admin','credit_officer','assistant','member') then
    raise exception 'invalid_sacco_role' using errcode = '22023';
  end if;
  if not exists (select 1 from auth.users u where u.id = p_new_user_id) then
    raise exception 'auth_user_not_found' using errcode = 'P0002';
  end if;
  select * into sacco_row from public.saccos s where s.id = p_sacco_id for update;
  if not found or sacco_row.status not in ('active','trial') then
    raise exception 'sacco_not_accepting_accounts' using errcode = '55000';
  end if;
  select exists (
    select 1 from public.sacco_memberships sm
    where sm.sacco_id = p_sacco_id and sm.user_id = p_actor_user_id
      and sm.status = 'active' and sm.role in ('chairperson','sacco_admin')
  ) into actor_is_sacco_admin;
  select exists (
    select 1 from public.platform_owners po
    where po.user_id = p_actor_user_id and po.active = true
  ) into actor_is_owner_bootstrap;
  if not actor_is_sacco_admin and not (
    actor_is_owner_bootstrap and p_role = 'chairperson' and sacco_row.status = 'trial'
  ) then
    raise exception 'sacco_admin_or_chairperson_required' using errcode = '42501';
  end if;
  select * into member_row from public.sacco_members m
  where m.id = p_member_row_id and m.sacco_id = p_sacco_id and m.status = 'active'
  for update;
  if not found then raise exception 'active_member_record_not_found' using errcode = 'P0002'; end if;
  if member_row.user_id is not null then raise exception 'member_login_already_provisioned' using errcode = '23505'; end if;
  loop
    attempt := attempt + 1;
    generated_id := 'AC' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 22));
    insert into private.account_login_aliases(account_id, user_id, active, must_change_pin)
    values (generated_id, p_new_user_id, true, true)
    on conflict (account_id) do nothing;
    exit when found;
    if attempt >= 5 then raise exception 'account_id_generation_failed' using errcode = '54000'; end if;
  end loop;
  update public.sacco_members set user_id = p_new_user_id, updated_at = now()
  where id = p_member_row_id and sacco_id = p_sacco_id;
  insert into public.sacco_memberships(sacco_id,user_id,role,status,member_number,created_by)
  values (p_sacco_id,p_new_user_id,p_role,'active',member_row.member_number,p_actor_user_id);
  insert into public.user_profiles(user_id,display_name,phone)
  values (p_new_user_id,member_row.full_name,member_row.phone)
  on conflict (user_id) do update set display_name=excluded.display_name,
    phone=coalesce(excluded.phone,public.user_profiles.phone),updated_at=now();
  insert into public.audit_logs(sacco_id,actor_user_id,event_type,entity_type,entity_id,metadata)
  values (p_sacco_id,p_actor_user_id,'member.login_provisioned','sacco_member',
    p_member_row_id::text,jsonb_build_object('role',p_role,'account_id',generated_id));
  return query select generated_id,member_row.member_id,member_row.member_number;
end;
$function$;