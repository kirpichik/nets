
#ifndef SOCKS_HANDLER_H
#define SOCKS_HANDLER_H

#include <vector>
#include <array>

#include "proxy-state.h"

namespace socks {

static constexpr size_t BUFFER_SIZE = 4096;

enum class state : std::uint8_t;
enum class address_type : std::uint8_t;
enum class response : std::uint8_t;

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

  std::array<uint8_t, BUFFER_SIZE> output;

  enum response parse_input(const uint8_t* buff, size_t len) noexcept;
  void handle_parse_result(enum response result) noexcept;
};

}  // namespace socks

#endif
