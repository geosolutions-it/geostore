-- Update the geostore database from 1.1 model to 1.2
-- It adds the support to multiple usergroups that can be associated to an user.
-- It add one more table: the many2many relation table between use and usergroup table
--      and creates the related the related foreign keys.

-- The script assumes that the tables are located into the schema called "geostore" 
--      if not find/replace geostore. with <yourschemaname>.

-- Tested only with postgres9.1

create table geostore.gs_usergroup_members (user_id int8 not null, group_id int8 not null, primary key (user_id, group_id));
alter table geostore.gs_usergroup_members add constraint FKFDE460DB62224F72 foreign key (user_id) references geostore.gs_user;
alter table geostore.gs_usergroup_members add constraint FKFDE460DB9EC981B7 foreign key (group_id) references geostore.gs_usergroup;