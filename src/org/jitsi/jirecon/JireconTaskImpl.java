/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

// TODO: Rewrite those import statements to package import statement.
import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;

import org.jitsi.jirecon.dtlscontrol.JireconDtlsControlManagerImpl;
import org.jitsi.jirecon.dtlscontrol.JireconSrtpControlManager;
import org.jitsi.jirecon.recorder.JireconRecorder;
import org.jitsi.jirecon.recorder.JireconRecorderImpl;
import org.jitsi.jirecon.session.JireconSession;
import org.jitsi.jirecon.session.JireconSessionImpl;
import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.jirecon.transport.JireconIceUdpTransportManagerImpl;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.service.neomedia.DtlsControl;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.SrtpControlType;
import org.jitsi.service.neomedia.format.AudioMediaFormat;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.XMPPConnection;

/**
 * This is an implementation of Jirecon
 * 
 * @author lishunyang
 * 
 */
public class JireconTaskImpl
    implements JireconTask, JireconEventListener
{
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    private JireconSession session;

    private JireconTransportManager transport;

    private JireconSrtpControlManager srtpControl;

    private JireconRecorder recorder;

    private JireconTaskInfo info = new JireconTaskInfo();

    private Logger logger;

    public JireconTaskImpl()
    {
        session = new JireconSessionImpl();
        transport = new JireconIceUdpTransportManagerImpl();
        srtpControl = new JireconDtlsControlManagerImpl();
        recorder = new JireconRecorderImpl();
        session.addEventListener(this);
        recorder.addEventListener(this);
        logger = Logger.getLogger(JireconTaskImpl.class);
        logger.setLevelAll();
    }

    @Override
    public void init(JireconConfiguration configuration, String conferenceJid,
        XMPPConnection connection, MediaService mediaService)
    {
        logger.debug(this.getClass() + " init");

        transport.init(configuration);
        srtpControl.init(mediaService);
        session.init(configuration, connection, conferenceJid, transport,
            srtpControl);
        recorder.init(configuration, mediaService, transport, srtpControl);
        updateState(JireconTaskState.INITIATING);
    }

    @Override
    public void uninit()
    {
        recorder.uninit();
        session.uninit();
        transport.uninit();
        srtpControl.uinit();
    }

    @Override
    public void start()
    {
        try
        {
            transport.harvestLocalCandidates();
            session.start();
        }
        catch (BindException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void stop()
    {
        logger.info(this.getClass() + " stop.");
        recorder.stop();
        session.stop();
    }

    @Override
    public void handleEvent(JireconEvent evt)
    {
        switch (evt.getEventId())
        {
        case SESSION_ABORTED:
            System.out.println(this.getClass() + " SESSION_ABORTED");
            updateState(JireconTaskState.ABORTED);
            fireEvent(new JireconEvent(this, JireconEventId.TASK_ABORTED));
            break;
        case SESSION_BUILDING:
            System.out.println(this.getClass() + " SESSION_BUILDING");
            updateState(JireconTaskState.SESSION_INITIATING);
            break;
        case SESSION_RECEIVE_INIT:
            System.out.println(this.getClass() + " SESSION_RECEIVE_INIT");
            session.sendAcceptPacket(recorder.getRecorderInfo());
            break;
        case SESSION_CONSTRUCTED:
            System.out.println(this.getClass() + " SESSION_CONSTRUCTED");
            updateState(JireconTaskState.SESSION_CONSTRUCTED);
            recorder.prepareMediaStreams(session.getSessionInfo());
            recorder.start();
            break;
        case RECORDER_ABORTED:
            System.out.println(this.getClass() + " RECORDER_ABORTED");
            updateState(JireconTaskState.ABORTED);
            fireEvent(new JireconEvent(this, JireconEventId.TASK_ABORTED));
            break;
        case RECORDER_BUILDING:
            System.out.println(this.getClass() + " RECORDER_BUILDING");
            updateState(JireconTaskState.RECORDER_INITIATING);
            break;
        case RECORDER_RECEIVING:
            System.out.println(this.getClass() + " RECORDER_RECEIVING");
            updateState(JireconTaskState.RECORDER_RECEIVING);
            break;
        case RECORDER_RECORDING:
            System.out.println(this.getClass() + " RECORDER_RECORDING");
            updateState(JireconTaskState.RECORDER_RECORDING);
            break;
        default:
            break;
        }
    }

    public void fireEvent(JireconEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }

    private void updateState(JireconTaskState state)
    {
        info.setState(state);
    }

    @Override
    public void addEventListener(JireconEventListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeEventListener(JireconEventListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public JireconTaskInfo getTaskInfo()
    {
        return info;
    }

}
