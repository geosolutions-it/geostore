alter table gs_resource add column creator varchar2(255 char);
alter table gs_resource add column editor varchar2(255 char);
alter table gs_resource add column advertised bool not null default true;
