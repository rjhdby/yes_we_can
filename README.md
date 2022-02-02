В sql базе данных есть таблица relations (product_id1 INT, product_id2 INT). В таблице несколько миллионов записей.

В Java приложении есть коллекция из 100000 айдишников (integer). Нужно получить список всех рекомендаций (строчек из таблицы relations), у которых product_id1 НЕ из этого списка.

Выборка в лоб
```SQL
SELECT * FROM relations WHERE NOT product_id1 IN(...100K ids...)
```

```
# ./gradlew test --tests HugeQueryTest
...
HugeQueryTest > queryTest() STANDARD_OUT
    Table created
    Data loaded
    Execute SELECT
    ResultSet size: 1900000
    Execution time: 2362 ms
    Fetch time: 347 ms
...
```