create table gs_tag (
    id number(19,0) not null,
    color varchar2(255 char) not null,
    description varchar2(255 char),
    "name" varchar2(255 char) not null,
    primary key (id)
);

create table gs_resource_tags (
    tag_id number(19,0) not null,
    resource_id number(19,0) not null,
    primary key (tag_id, resource_id)
);

-- Add foreign key constraints to gs_resource_tags
alter table gs_resource_tags
    add constraint fk_resource_tags_resource
    foreign key (resource_id)
    references gs_resource(id);

alter table gs_resource_tags
    add constraint fk_resource_tags_tag
    foreign key (tag_id)
    references gs_tag(id);