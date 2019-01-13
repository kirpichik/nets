
#ifndef SOCKS_HANDLER_H
#define SOCKS_HANDLER_H

#include <deque>
#include <array>
#include <vector>

#include "proxy-state.h"

namespace socks {

enum class state : std::uint8_t;
enum class address_type : std::uint8_t;
enum class response : std::uint8_t;

void resolve_handler(void* arg, int status, int timeouts, struct hostent* hostent) noexcept;

class socks_handler final : public proxy::proxy_handler {
 public:
  socks_handler(std::shared_ptr<proxy::proxy_state> state, int fd) noexcept;

  bool handle_receive() noexcept override;
  bool handle_send() noexcept override;

 private:
  enum state st;

  uint8_t auth_methods_left;
  bool auth_method_found = false;

  enum address_type addr_type;
  std::vector<uint8_t> address;
  uint16_t port;
  int pair_socket;

  std::deque<uint8_t> input;
  std::deque<uint8_t> output;

  enum response parse_input() noexcept;
  void handle_parse_result(enum response result) noexcept;

  friend void resolve_handler(void* arg, int status, int timeouts, struct hostent* hostent) noexcept;
};

}  // namespace socks

#endif
