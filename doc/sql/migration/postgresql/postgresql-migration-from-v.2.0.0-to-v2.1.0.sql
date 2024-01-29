alter table gs_resource add column creator varchar(255);
alter table gs_resource add column editor varchar(255);
alter table gs_resource add column advertised bool not null default true;
