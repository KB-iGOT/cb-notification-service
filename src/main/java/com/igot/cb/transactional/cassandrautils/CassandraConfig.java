package com.igot.cb.transactional.cassandrautils;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;

@Getter
@Setter
@NoArgsConstructor
public abstract class CassandraConfig extends AbstractCassandraConfiguration {
    protected String contactPoints;
    protected int port;
    protected String keyspaceName;
}
