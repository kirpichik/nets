
#ifndef FORWARD_HANDLER_H
#define FORWARD_HANDLER_H

#include "proxy-state.h"

namespace fwd {

class forward_handler final : public proxy::proxy_handler {
 public:
  forward_handler(std::shared_ptr<proxy::proxy_state> state, int fd) noexcept
      : proxy_handler(state, fd) {}

  bool handle_receive() noexcept override;
  bool handle_send() noexcept override;
};

}  // namespace fwd

#endif
