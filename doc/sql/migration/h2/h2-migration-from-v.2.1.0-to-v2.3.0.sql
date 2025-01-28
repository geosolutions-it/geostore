CREATE TABLE gs_tag (
    id BIGINT NOT NULL,
    color VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    CONSTRAINT gs_tag_pkey PRIMARY KEY (id)
);

CREATE TABLE gs_resource_tags (
    tag_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    CONSTRAINT gs_resource_tags_pkey PRIMARY KEY (tag_id, resource_id)
);

-- Add foreign key constraints to gs_resource_tags
ALTER TABLE gs_resource_tags
    ADD CONSTRAINT fk_resource_tags_resource
    FOREIGN KEY (resource_id)
    REFERENCES gs_resource(id);

ALTER TABLE gs_resource_tags
    ADD CONSTRAINT fk_resource_tags_tag
    FOREIGN KEY (tag_id)
    REFERENCES gs_tag(id);