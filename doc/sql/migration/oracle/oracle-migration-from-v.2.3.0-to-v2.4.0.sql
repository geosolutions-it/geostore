create table gs_ip_range (
    id number(19,0) not null,
    cidr varchar2(50 char) not null,
    description varchar2(255 char),
    constraint gs_ip_range_pkey primary key (id),
    constraint gs_ip_range_unique_cidr unique (cidr)
);

create table gs_security_ip_range (
    security_id number(19,0) not null,
    ip_range_id number(19,0) not null,
    constraint gs_security_ip_range_unique_security_id_ip_range_id unique (security_id, ip_range_id),
    constraint fk_gs_security foreign key (security_id) references gs_security(id),
    constraint fk_gs_ip_range foreign key (ip_range_id) references gs_ip_range(id)
);

create index idx_security_ip_range_ip_range_id on gs_security_ip_range (ip_range_id);
create index idx_security_ip_range_security_id on gs_security_ip_range (security_id);
