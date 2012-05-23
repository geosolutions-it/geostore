-- CREATE SCHEMA geostore 
CREATE user geostore LOGIN PASSWORD 'geostore' NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE;

CREATE SCHEMA geostore;

GRANT USAGE ON SCHEMA geostore TO geostore ;
GRANT ALL ON SCHEMA geostore TO geostore ;

--GRANT SELECT ON public.spatial_ref_sys to geostore;
--GRANT SELECT,INSERT,DELETE ON public.geometry_columns to geostore;

alter user geostore set search_path to geostore , public;

-- CREATE SCHEMA georepo_test
CREATE user geostore_test LOGIN PASSWORD 'geostore_test' NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE;

CREATE SCHEMA geostore_test;

GRANT USAGE ON SCHEMA geostore_test TO geostore_test;
GRANT ALL ON SCHEMA geostore_test TO geostore_test;

--GRANT SELECT ON public.spatial_ref_sys to geostore_test;
--GRANT SELECT,INSERT,DELETE ON public.geometry_columns to geostore_test;

alter user geostore_test set search_path to geostore_test, public;
