-- Zmieniamy krok sekwencji w bazie danych na 50, aby pasował do optymalizatora Hibernate
ALTER SEQUENCE tx_seq INCREMENT BY 50;