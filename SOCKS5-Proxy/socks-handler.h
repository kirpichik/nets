
#ifndef SOCKS_HANDLER_H
#define SOCKS_HANDLER_H

#include <vector>

#include "proxy-state.h"

namespace socks {

enum class state : std::uint8_t;
enum class address_type : std::uint8_t;

class socks_handler final : public proxy::proxy_handler {
 public:
  socks_handler(std::shared_ptr<proxy::proxy_state> state, int fd) noexcept
      : proxy::proxy_handler(state, fd) {}

  bool handle_receive() noexcept override;
  bool handle_send() noexcept override;

 private:
  enum state st;
  enum address_type addr_type;
  std::vector<char> address;
  uint16_t port;
};

}  // namespace socks

#endif
