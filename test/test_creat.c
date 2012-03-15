#include "stdio.h"
#include "stdlib.h"

int main(int agrc, char *argv[]) {

  char* name = "hello";
  int fd = creat(name);
  printf("fd: %d\n", fd);
  return 0;
}
