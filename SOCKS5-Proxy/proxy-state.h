
#ifndef PROXY_HANDLER_H
#define PROXY_HANDLER_H

#include <ares.h>
#include <sys/select.h>
#include <map>
#include <memory>

namespace proxy {

class proxy_state;

class proxy_handler {
 public:
  proxy_handler(std::shared_ptr<proxy_state> state, int fd) noexcept
      : state(state), fd(fd), req_read(true) {}

  virtual bool handle_receive() = 0;
  virtual bool handle_send() = 0;

  bool is_read_required() const { return req_read; }

  bool is_write_required() const { return req_write; }

  virtual ~proxy_handler() {}

 protected:
  const std::shared_ptr<proxy_state> state;
  const int fd;
  bool req_read;
  bool req_write;
};

class proxy_state final {
 public:
  proxy_state(int server_socket) noexcept;
  proxy_state(const proxy_state&) = delete;

  int run();
  void register_handler(int socket,
                        std::unique_ptr<proxy_handler> handler) noexcept;
  void unregister_handler(int socket) noexcept;

  using resolve_callback = void (*)(void*, int, int, struct hostent*);
  void resolve(const std::string& hostname,
               resolve_callback callback,
               void* arg) noexcept;

  proxy_state& operator=(const proxy_state&) = delete;

  ~proxy_state() noexcept;

 private:
  ares_channel channel;
  const int server_socket;
  std::map<int, std::unique_ptr<proxy_handler>> handlers;

  void build_fds(fd_set* read, fd_set* write, fd_set* except) noexcept;
};

class server_handler final : public proxy_handler {
 public:
  server_handler(std::shared_ptr<proxy_state> state, int fd) noexcept
      : proxy_handler(state, fd) {}

  bool handle_receive() noexcept override;
  bool handle_send() noexcept override;
};

}  // namespace proxy

#endif
