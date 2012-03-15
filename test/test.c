#include "stdio.h"
#include "stdlib.h"

int main(int agrc, char *argv[]) {
  int BUFFERSIZE = 64;
  char buffer[BUFFERSIZE];
  printf("start: ");
  readline(buffer, BUFFERSIZE);
  printf("%s\n", buffer);
  return 0;
}
