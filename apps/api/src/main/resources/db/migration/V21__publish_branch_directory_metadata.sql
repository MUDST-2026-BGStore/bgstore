alter table branch
    add column phone varchar(32),
    add column status varchar(16) not null default 'ACTIVE',
    add column latitude double precision,
    add column longitude double precision;

alter table branch
    add constraint branch_status_known check (status in ('ACTIVE', 'INACTIVE')),
    add constraint branch_latitude_range check (latitude is null or latitude between -90 and 90),
    add constraint branch_longitude_range check (longitude is null or longitude between -180 and 180),
    add constraint branch_coordinates_complete check ((latitude is null) = (longitude is null));
