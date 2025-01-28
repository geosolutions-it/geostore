create table gs_tag (
    id int8 not null,
    color varchar(255) not null,
    description varchar(255) null,
    "name" varchar(255) not null,
    constraint gs_tag_pkey primary key (id)
);

create table gs_resource_tags (
    tag_id int8 not null,
    resource_id int8 not null,
    constraint gs_resource_tags_pkey primary key (tag_id, resource_id)
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