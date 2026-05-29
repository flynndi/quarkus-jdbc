drop table extra_item if exists;

create table extra_item(
    id integer not null,
    name varchar(50) not null
);

insert into extra_item(id, name) values
    (1, 'alpha'),
    (2, 'beta')
;
