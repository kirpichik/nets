
#ifndef FORWARD_HANDLER_H
#define FORWARD_HANDLER_H

#include <vector>

#include "proxy-state.h"

namespace fwd {

class forward_handler final : public proxy::proxy_handler {
 public:
  forward_handler(std::shared_ptr<proxy::proxy_state> state, int fd, std::vector<uint8_t>& buffer) noexcept
      : proxy_handler(state, fd), buffer(buffer) {}

  forward_handler(std::shared_ptr<proxy::proxy_state> state, int fd) noexcept
  : proxy_handler(state, fd) {}

  bool handle_receive() noexcept override;
  bool handle_send() noexcept override;

  void set_pair(forward_handler* pair) noexcept {
    this->pair = pair;
  }

 private:
  bool in_down;
  bool out_down;
  forward_handler* pair;
  std::vector<uint8_t> buffer;
};

}  // namespace fwd

#endif
