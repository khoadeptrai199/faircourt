-- Runs once, only when the Postgres volume is first created (official image behavior).
-- Separate database for backend integration tests so tests never touch demo data.
CREATE DATABASE faircourt_test;
