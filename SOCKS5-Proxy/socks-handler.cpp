
#include <sys/socket.h>
#include <iostream>

#include "socks-handler.h"

namespace socks {

static constexpr std::uint8_t SOCKS5_VERSION = 0x05;
static constexpr std::uint8_t RESERVED_BYTE = 0x00;
static constexpr std::uint8_t AUTH_NOT_REQUIRED = 0x00;
static constexpr std::uint8_t USUPPORTED_AUTH_METHOD = 0xFF;

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
  GRANTED = 0x00,
  FAILURE = 0x01,
  NOT_ALLOWED = 0x02,
  NETWORK_UNREACHABLE = 0x03,
  HOST_UNREACHABLE = 0x04,
  CONNECTION_REFUSED = 0x05,
  TTL_EXPIRED = 0x06,
  PROTOCOL_ERROR = 0x07,
  ADDRESS_TYPE_NOT_SUPPORTED = 0x08,

  // Спец. для обработчика (не входят в протокол)
  AUTH_PROTOCOL_ERROR,
  UNFINISHED,
  UNSUPPORTED_AUTH_METHOD
};

socks_handler::socks_handler(std::shared_ptr<proxy::proxy_state> state,
                             int fd) noexcept
    : proxy::proxy_handler(state, fd), st(state::AUTH_VERSION) {}

enum response socks_handler::parse_input(const uint8_t* buff,
                                         size_t len) noexcept {
  size_t pos = 0;
  uint8_t value;

  while (pos < len) {
    switch (st) {
      case state::AUTH_VERSION:
        if (buff[pos++] != SOCKS5_VERSION)
          return response::AUTH_PROTOCOL_ERROR;
        st = state::AUTH_METHODS_COUNT;
        break;

      case state::AUTH_METHODS_COUNT:
        auth_methods_left = buff[pos++];
        if (auth_methods_left == 0)
          return response::UNSUPPORTED_AUTH_METHOD;
        st = state::AUTH_METHODS_LIST;
        break;

      case state::AUTH_METHODS_LIST:
        if (buff[pos++] == AUTH_NOT_REQUIRED)
          auth_method_found = true;
        if (--auth_methods_left == 0) {
          if (auth_method_found) {
            st = state::REQUEST_VERSION;
            break;
          }
          return response::UNSUPPORTED_AUTH_METHOD;
        }
        break;

      case state::REQUEST_VERSION:
        if (buff[pos++] != SOCKS5_VERSION)
          return response::PROTOCOL_ERROR;
        st = state::REQUEST_COMMAND;
        break;

      case state::REQUEST_COMMAND:
        if (buff[pos++] != static_cast<uint8_t>(command::ESTABLISH_TCP))
          return response::PROTOCOL_ERROR;
        st = state::REQUEST_RESERVED_BYTE;
        break;

      case state::REQUEST_RESERVED_BYTE:
        if (buff[pos++] != RESERVED_BYTE)
          return response::PROTOCOL_ERROR;
        st = state::REQUEST_ADDRESS_TYPE;
        break;

      case state::REQUEST_ADDRESS_TYPE:
        value = buff[pos++];
        if (value == static_cast<uint8_t>(address_type::IPV4)) {
          addr_type = address_type::IPV4;
          address.reserve(4);
        } else if (value == static_cast<uint8_t>(address_type::IPV6)) {
          addr_type = address_type::IPV6;
          address.reserve(16);
        } else
          addr_type = address_type::HOSTNAME;
        st = state::REQUEST_ADDRESS;
        break;

      case state::REQUEST_ADDRESS:
        value = buff[pos++];
        address.push_back(value);
        if ((addr_type == address_type::IPV4 && address.size() == 4) ||
            (addr_type == address_type::IPV6 && address.size() == 16) ||
            (addr_type == address_type::HOSTNAME && value == '\0')) {
          st = state::REQUEST_PORT_FIRST;
          break;
        }
        break;

      case state::REQUEST_PORT_FIRST:
        port = buff[pos++];
        st = state::REQUEST_PORT_SECOND;
        break;

      case state::REQUEST_PORT_SECOND:
        port = (port << 8) + buff[pos++];
        return response::GRANTED;
    }
  }

  return response::UNFINISHED;
}

void socks_handler::handle_parse_result(enum response result) noexcept {
  switch (result) {
    case response::UNFINISHED:
      break;

    case response::AUTH_PROTOCOL_ERROR:
    case response::UNSUPPORTED_AUTH_METHOD:
      // TODO - send auth error
      break;

    case response::GRANTED:
      // TODO - resolve hostname and establish connection
      break;

    default:
      // TODO - send protocol error
      break;
  }
}

bool socks_handler::handle_receive() noexcept {
  uint8_t buffer[BUFFER_SIZE];
  ssize_t len;

  if ((len = recv(fd, buffer, BUFFER_SIZE, 0)) < 0) {
    std::perror("Cannot receive data in socks handle");
    return false;
  } else if (len == 0) {
    std::cerr << "Unexpected end-of-stream in socks handler" << std::endl;
    return false;
  }

  handle_parse_result(parse_input(buffer, len));
  return true;
}

bool socks_handler::handle_send() noexcept {
  return false;
}

}  // namespace socks
