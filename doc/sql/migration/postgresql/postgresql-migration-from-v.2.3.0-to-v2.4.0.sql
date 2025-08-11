CREATE TABLE gs_ip_range (
	id int8 NOT NULL,
	cidr varchar(50) NOT NULL,
	description varchar(255) NULL,
	CONSTRAINT gs_ip_range_pkey PRIMARY KEY (id),
	CONSTRAINT gs_ip_range_unique_cidr UNIQUE (cidr)
);

CREATE TABLE gs_security_ip_range (
	security_id int8 NOT NULL,
	ip_range_id int8 NOT NULL,
	CONSTRAINT gs_security_ip_range_unique_security_id_ip_range_id UNIQUE (security_id, ip_range_id),
	CONSTRAINT fk_gs_security FOREIGN KEY (security_id) REFERENCES gs_security(id),
	CONSTRAINT fk_gs_ip_range FOREIGN KEY (ip_range_id) REFERENCES gs_ip_range(id)
);
CREATE INDEX idx_security_ip_range_ip_range_id ON gs_security_ip_range USING btree (ip_range_id);
CREATE INDEX idx_security_ip_range_security_id ON gs_security_ip_range USING btree (security_id);