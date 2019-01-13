
#include <sys/socket.h>
#include <iostream>

#include "socks-handler.h"

namespace socks {

static constexpr size_t BUFFER_SIZE = 4096;
static constexpr std::uint8_t SOCKS5_VERSION = 0x05;
static constexpr std::uint8_t RESERVED_BYTE = 0x00;

enum class state : std::uint8_t {
  AUTH_VERSION,
  AUTH_METHODS_COUNT,
  AUTH_METHODS_LIST,

  REQUEST_VERSION,
  REQUEST_COMMAND,
  REQUEST_RESERVED_BYTE,
  REQUEST_ADDRESS_TYPE,
  REQUEST_ADDRESS,
  REQUEST_PORT_FIRST,
  REQUEST_PORT_SECOND
};

enum class command : std::uint8_t {
  ESTABLISH_TCP = 0x01,
  BIND_TCP = 0x02,
  BIND_UDP = 0x03
};

enum class address_type : std::uint8_t {
  IPV4 = 0x01,
  HOSTNAME = 0x03,
  IPV6 = 0x04
};

enum class response : std::uint8_t {
  GRANTED,
  FAILURE,
  NOT_ALLOWED,
  NETWORK_UNREACHABLE,
  HOST_UNREACHABLE,
  CONNECTION_REFUSED,
  TTL_EXPIRED,
  PROTOCOL_ERROR,
  ADDRESS_TYPE_NOT_SUPPORTED
};

bool socks_handler::handle_receive() noexcept {
  char buffer[BUFFER_SIZE];
  ssize_t len;

  if ((len = recv(fd, buffer, BUFFER_SIZE, 0)) < 0) {
    std::perror("Cannot receive data in socks handle");
    return false;
  }

  // TODO - switch-case

  return true;
}

bool socks_handler::handle_send() noexcept {
  return false;
}

}  // namespace socks
