# Поиск копий себя в сети

Поиск основан на технологии Multicast и поддерживает, как IPv4, так и IPv6 сети.

## Решение проблем:

В некоторых случаях при попытке использовать Multicast для IPv4 происходит ошибка:

```
java.net.SocketException: Can't assign requested address
```

Это связано с тем, что метод:

```
java.net.NetworkInterface.getDefault()
```

Возвращает адрес в IPv6 формате и не позволяет использовать Multicast в IPv4.

Исправлением является запуск виртуальной машины с ключом:

```
-Djava.net.preferIPv4Stack=true
```
