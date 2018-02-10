
# --- !Ups

create table "event" (
  "id" bigint not null auto_increment,
  "user" varchar not null,
  "kind" varchar not null,
  "milliseconds_from_epoch" timestamp not null,
  "hours_from_epoch" bigint not null
);


# --- !Downs

drop table if exists "event";