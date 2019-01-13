
#include <iostream>
#include <sys/socket.h>
#include <unistd.h>
#include <array>

#include "forward-handler.h"

namespace fwd {

bool forward_handler::handle_receive() noexcept {
  std::array<uint8_t, BUFFER_SIZE> buff;
  ssize_t len;

  if ((len = recv(fd, buff.data(), BUFFER_SIZE - buffer.size(), 0)) < 0) {
    std::perror("Cannot read data in forwarder");
    state->unregister_handler(pair->fd);
    return false;
  } else if (len == 0) {
    shutdown(fd, SHUT_RD);
    req_read = false;
    in_down = pair->out_down = true;
  }

  std::copy(buff.begin(), buff.end(), std::back_inserter(buffer));
  req_write = true;
  if (buffer.size() >= BUFFER_SIZE)
    req_read = false;

  return true;
}

bool forward_handler::handle_send() noexcept {
  ssize_t len = send(fd, &pair->buffer[0], pair->buffer.size(), 0);
  if (len < 0) {
    std::perror("Cannot send data in forwarder");
    state->unregister_handler(pair->fd);
    return false;
  }

  buffer.erase(buffer.begin(), buffer.begin() + len);
  if (!out_down)
    pair->req_read = true;
  if (buffer.empty()) {
    req_write = false;
    if (out_down)
      shutdown(fd, SHUT_WR);
  }

  // Обе стороны закрыли соединение
  if (in_down && out_down && buffer.empty() && pair->buffer.empty()) {
    state->unregister_handler(pair->fd);
    return false;
  }

  return true;
}

}  // namespace fwd
