
#include <sys/socket.h>
#include <iostream>
#include <string.h>
#include <unistd.h>

#include "forward-handler.h"

#include "socks-handler.h"

namespace socks {

static constexpr std::uint8_t SOCKS5_VERSION = 0x05;
static constexpr std::uint8_t RESERVED_BYTE = 0x00;
static constexpr std::uint8_t AUTH_NOT_REQUIRED = 0x00;
static constexpr std::uint8_t UNSUPPORTED_AUTH_METHOD = 0xFF;

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
  REQUEST_PORT_SECOND,

  WAIT_FOR_RESOLVE,
  TRANSFORM_TO_FORWARD,
  DROP_CONNECTION
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
  AUTH_ACCEPT,
  AUTH_PROTOCOL_ERROR,
  UNFINISHED,
  UNSUPPORTED_AUTH_METHOD
};

socks_handler::socks_handler(std::shared_ptr<proxy::proxy_state> state,
                             int fd) noexcept
    : proxy::proxy_handler(state, fd), st(state::AUTH_VERSION) {}

static void push_error(std::deque<uint8_t>& output, uint8_t code) noexcept {
  output.push_back(code);
  output.push_back(RESERVED_BYTE);
  output.push_back(static_cast<uint8_t>(address_type::HOSTNAME));
  output.push_back('\0');
  output.push_back(0);
  output.push_back(0);
}

void resolve_handler(void* arg, int status, int timeouts, hostent* hostent) noexcept {
  socks_handler* handler = reinterpret_cast<socks_handler*>(arg);
  handler->req_write = true;
  handler->output.push_back(SOCKS5_VERSION);

  if (status != ARES_SUCCESS) {
    push_error(handler->output, static_cast<uint8_t>(response::HOST_UNREACHABLE));
    handler->st = state::DROP_CONNECTION;
    return;
  }

  int sock;
  if ((sock = socket(hostent->h_addrtype, SOCK_STREAM, 0)) < 0) {
    std::perror("Cannot create forward socket");
    push_error(handler->output, static_cast<uint8_t>(response::FAILURE));
    handler->st = state::DROP_CONNECTION;
    return;
  }

  sockaddr_in addr = {
    .sin_family = static_cast<sa_family_t>(hostent->h_addrtype),
    .sin_port = htons(handler->port),
  };
  std::memcpy(&addr, hostent->h_addr, hostent->h_length);

  if (connect(sock, reinterpret_cast<sockaddr*>(&addr), sizeof(addr))) {
    std::perror("Cannot connect forward socket");
    close(sock);
    push_error(handler->output, static_cast<uint8_t>(response::HOST_UNREACHABLE));
    handler->st = state::DROP_CONNECTION;
  }

  handler->pair_socket = sock;
  handler->output.push_back(static_cast<uint8_t>(response::GRANTED));
  handler->output.push_back(RESERVED_BYTE);
  // TODO - send self-ip
  handler->output.push_back(static_cast<uint8_t>(address_type::HOSTNAME));
  handler->output.push_back('\0');
  handler->output.push_back(0);
  handler->output.push_back(0);
  handler->st = state::TRANSFORM_TO_FORWARD;
}

enum response socks_handler::parse_input() noexcept {
  while (!input.empty()) {
    uint8_t value = input.front();
    input.pop_front();

    switch (st) {
      case state::AUTH_VERSION:
        if (value != SOCKS5_VERSION)
          return response::AUTH_PROTOCOL_ERROR;
        st = state::AUTH_METHODS_COUNT;
        break;

      case state::AUTH_METHODS_COUNT:
        auth_methods_left = value;
        if (auth_methods_left == 0)
          return response::UNSUPPORTED_AUTH_METHOD;
        st = state::AUTH_METHODS_LIST;
        break;

      case state::AUTH_METHODS_LIST:
        if (value == AUTH_NOT_REQUIRED)
          auth_method_found = true;
        if (--auth_methods_left == 0) {
          if (auth_method_found) {
            st = state::REQUEST_VERSION;
            return response::AUTH_ACCEPT;
          }
          return response::UNSUPPORTED_AUTH_METHOD;
        }
        break;

      case state::REQUEST_VERSION:
        if (value != SOCKS5_VERSION)
          return response::PROTOCOL_ERROR;
        st = state::REQUEST_COMMAND;
        break;

      case state::REQUEST_COMMAND:
        if (value != static_cast<uint8_t>(command::ESTABLISH_TCP))
          return response::PROTOCOL_ERROR;
        st = state::REQUEST_RESERVED_BYTE;
        break;

      case state::REQUEST_RESERVED_BYTE:
        if (value != RESERVED_BYTE)
          return response::PROTOCOL_ERROR;
        st = state::REQUEST_ADDRESS_TYPE;
        break;

      case state::REQUEST_ADDRESS_TYPE:
        if (value == static_cast<uint8_t>(address_type::IPV4)) {
          addr_type = address_type::IPV4;
          address.reserve(4);
        } else if (value == static_cast<uint8_t>(address_type::IPV6)) {
          addr_type = address_type::IPV6;
          address.reserve(16);
        } else if (value == static_cast<uint8_t>(address_type::HOSTNAME))
          addr_type = address_type::HOSTNAME;
        else
          return response::ADDRESS_TYPE_NOT_SUPPORTED;
        st = state::REQUEST_ADDRESS;
        break;

      case state::REQUEST_ADDRESS:
        address.push_back(value);
        if ((addr_type == address_type::IPV4 && address.size() == 4) ||
            (addr_type == address_type::IPV6 && address.size() == 16) ||
            (addr_type == address_type::HOSTNAME && value == '\0')) {
          st = state::REQUEST_PORT_FIRST;
          break;
        }
        break;

      case state::REQUEST_PORT_FIRST:
        port = value;
        st = state::REQUEST_PORT_SECOND;
        break;

      case state::REQUEST_PORT_SECOND:
        port = (port << 8) + value;
        return response::GRANTED;
      
      default:
        return response::FAILURE;
    }
  }

  return response::UNFINISHED;
}

void socks_handler::handle_parse_result(enum response result) noexcept {
  if (result != response::UNFINISHED) {
    req_write = true;
    output.push_back(SOCKS5_VERSION);
  }

  switch (result) {
    case response::UNFINISHED:
      break;

    case response::AUTH_ACCEPT:
      output.push_back(AUTH_NOT_REQUIRED);
      break;

    case response::GRANTED:
      if (addr_type == address_type::HOSTNAME) {
        state->resolve(std::string(address.begin(), address.end()), &resolve_handler, this);
        st = state::WAIT_FOR_RESOLVE;
      } else {
        output.push_back(static_cast<uint8_t>(response::GRANTED));
        output.push_back(RESERVED_BYTE);
        // TODO - send self-ip
        output.push_back(static_cast<uint8_t>(address_type::HOSTNAME));
        output.push_back('\0');
        output.push_back(0);
        output.push_back(0);
        st = state::TRANSFORM_TO_FORWARD;
      }
      break;
    
    case response::AUTH_PROTOCOL_ERROR:
    case response::UNSUPPORTED_AUTH_METHOD:
      output.push_back(UNSUPPORTED_AUTH_METHOD);
      st = state::DROP_CONNECTION;
      break;

    default:
      output.push_back(static_cast<uint8_t>(result));
      output.push_back(RESERVED_BYTE);
      output.push_back(static_cast<uint8_t>(address_type::HOSTNAME));
      output.push_back('\0');
      output.push_back(0);
      output.push_back(0);
      st = state::DROP_CONNECTION;
      break;
  }
}

bool socks_handler::handle_receive() noexcept {
  std::array<uint8_t, BUFFER_SIZE> buffer;
  ssize_t len;

  if ((len = recv(fd, buffer.data(), BUFFER_SIZE, 0)) < 0) {
    std::perror("Cannot receive data in socks handle");
    return false;
  } else if (len == 0) {
    std::cerr << "Unexpected end-of-stream in socks handler" << std::endl;
    return false;
  }

  for (const auto& i : buffer)
    input.push_back(i);

  handle_parse_result(parse_input());
  switch (st) {
    case state::WAIT_FOR_RESOLVE:
    case state::TRANSFORM_TO_FORWARD:
    case state::DROP_CONNECTION:
      req_read = false;
    default:
      break;
  }
  return true;
}

bool socks_handler::handle_send() noexcept {
  std::vector<uint8_t> buffer(std::min(output.size(), BUFFER_SIZE));
  while (!output.empty() && buffer.size() < BUFFER_SIZE) {
    buffer.push_back(output.front());
    output.pop_front();
  }

  ssize_t count;
  if ((count = send(fd, &buffer[0], buffer.size(), 0)) < 0) {
    std::perror("Cannot send data from socks handler");
    return false;
  }

  // Возвращаем остатки
  for (size_t left = buffer.size() - count; left > 0; left--)
    output.push_front(buffer[buffer.size() - left]);

  // Все отправлено, переходим к проксированию
  if (output.empty()) {
    std::vector<uint8_t> buff(input.begin(), input.end());
    fwd::forward_handler* src = new fwd::forward_handler(state, fd, buff);
    fwd::forward_handler* dst = new fwd::forward_handler(state, pair_socket);
    dst->set_pair(src);
    src->set_pair(dst);
    state->register_handler(pair_socket, std::unique_ptr<fwd::forward_handler>(dst));
    // Самозаменяемся
    state->register_handler(fd, std::unique_ptr<fwd::forward_handler>(src));
    return true;
  }

  return true;
}

}  // namespace socks
